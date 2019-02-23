/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.handlers.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpHeadersValues;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.core.endpoint.lifecycle.GroupLifecyleManager;
import io.gravitee.gateway.core.invoker.EndpointInvoker;
import io.gravitee.gateway.core.processor.ProcessorFailure;
import io.gravitee.gateway.core.processor.StreamableProcessor;
import io.gravitee.gateway.core.proxy.DirectProxyConnection;
import io.gravitee.gateway.handlers.api.definition.Api;
import io.gravitee.gateway.handlers.api.processor.OnErrorProcessorChainFactory;
import io.gravitee.gateway.handlers.api.processor.RequestProcessorChainFactory;
import io.gravitee.gateway.handlers.api.processor.ResponseProcessorChainFactory;
import io.gravitee.gateway.policy.PolicyManager;
import io.gravitee.gateway.reactor.Reactable;
import io.gravitee.gateway.reactor.handler.AbstractReactorHandler;
import io.gravitee.gateway.resource.ResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApiReactorHandler extends AbstractReactorHandler implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(ApiReactorHandler.class);

    @Autowired
    protected Api api;

    /**
     * Invoker is the connector to access the remote backend / endpoint.
     * If not override by a policy, default invoker is {@link EndpointInvoker}.
     */
    @Autowired
    private Invoker invoker;

    @Autowired
    private ObjectMapper mapper;

    private String contextPath;

    @Autowired
    private RequestProcessorChainFactory requestProcessorChain;

    @Autowired
    private ResponseProcessorChainFactory responseProcessorChain;

    @Autowired
    private OnErrorProcessorChainFactory errorProcessorChain;

    @Override
    protected void doHandle(final ExecutionContext context) {
        final Request request = context.request();

        // Pause the request and resume it as soon as all the stream are plugged and we have processed the HEAD part
        // of the request. (see handleProxyInvocation method).
        request.pause();

        context.setAttribute(ExecutionContext.ATTR_CONTEXT_PATH, request.contextPath());
        context.setAttribute(ExecutionContext.ATTR_API, api.getId());
        context.setAttribute(ExecutionContext.ATTR_INVOKER, invoker);

        // Prepare request metrics
        request.metrics().setApi(api.getId());
        request.metrics().setPath(request.pathInfo());

        // It's time to process incoming client request
        handleClientRequest(context);
    }

    private void handleClientRequest(final ExecutionContext context) {
        requestProcessorChain
                .create()
                .handler(result -> handleProxyInvocation(context, result))
                .streamErrorHandler(failure -> handleError(context, failure))
                .errorHandler(failure -> handleError(context, failure))
                .exitHandler(__ -> {
                    context.request().resume();
                    context.response().end();
                })
                .handle(context);
    }

    private void handleProxyInvocation(final ExecutionContext context, final StreamableProcessor<ExecutionContext, Buffer> processor) {
        // Call an invoker to get a proxy connection (connection to an underlying backend, mainly HTTP)
        Invoker upstreamInvoker = (Invoker) context.getAttribute(ExecutionContext.ATTR_INVOKER);

        context.request().metrics().setApiResponseTimeMs(System.currentTimeMillis());

        upstreamInvoker.invoke(context, processor, connection -> {
            connection.responseHandler(proxyResponse -> handleProxyResponse(context, proxyResponse));

            // Override the stream error handler to be able to cancel connection to backend
            processor.streamErrorHandler(failure -> {
                connection.cancel();
                handleError(context, failure);
            });
        });

        //TODO: not sure it should be done here, check FailoverInvoker
        //context.setRequest(invokeRequest);

        context.request()
                .bodyHandler(processor::write)
                .endHandler(result -> processor.end());
    }

    private void handleProxyResponse(final ExecutionContext context, final ProxyResponse proxyResponse) {
        if (proxyResponse == null || proxyResponse instanceof DirectProxyConnection.DirectResponse) {
            context.response().status((proxyResponse == null) ? HttpStatusCode.SERVICE_UNAVAILABLE_503 : proxyResponse.status());
            context.response().end();
        } else {
            handleClientResponse(context, proxyResponse);
        }
    }

    private void handleClientResponse(final ExecutionContext context, final ProxyResponse proxyResponse) {
        // Set the status
        context.response().status(proxyResponse.status());

        // Copy HTTP headers
        proxyResponse.headers().forEach((headerName, headerValues) -> context.response().headers().put(headerName, headerValues));

        responseProcessorChain
                .create()
                .errorHandler(failure -> handleError(context, failure))
                .streamErrorHandler(failure -> handleError(context, failure))
                .exitHandler(__ -> context.response().end())
                .handler(stream -> {
                    stream
                            .bodyHandler(chunk -> context.response().write(chunk))
                            .endHandler(__ -> context.response().end());

                    proxyResponse
                            .bodyHandler(buffer -> {
                                stream.write(buffer);

                                if (context.response().writeQueueFull()) {
                                    proxyResponse.pause();
                                    context.response().drainHandler(aVoid -> proxyResponse.resume());
                                }
                            }).endHandler(__ -> {
                                stream.end();
                                context.request().metrics().setApiResponseTimeMs(System.currentTimeMillis() -
                                        context.request().metrics().getApiResponseTimeMs());
                            });

                    // Resume response read
                    proxyResponse.resume();
                })
                .handle(context);
    }


    private void handleError(ExecutionContext context, ProcessorFailure failure) {
        errorProcessorChain
                .create()
                .handler(__ -> handleProcessorFailure(failure, context.response()))
                .handle(context);
    }

    private void handleProcessorFailure(ProcessorFailure failure, Response response) {
        response.status(failure.statusCode());

        response.headers().set(HttpHeaders.CONNECTION, HttpHeadersValues.CONNECTION_CLOSE);

        if (failure.message() != null) {
            try {
                Buffer payload;
                if (failure.contentType().equalsIgnoreCase(MediaType.APPLICATION_JSON)) {
                    payload = Buffer.buffer(failure.message());
                } else {
                    String contentAsJson = mapper.writeValueAsString(new ProcessorFailureAsJson(failure));
                    payload = Buffer.buffer(contentAsJson);
                }
                response.headers().set(HttpHeaders.CONTENT_LENGTH, Integer.toString(payload.length()));
                response.headers().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                response.write(payload);
            } catch (JsonProcessingException jpe) {
                logger.error("Unable to transform a policy result into a json payload", jpe);
            }
        }

        response.end();
    }

    private class ProcessorFailureAsJson {

        @JsonProperty
        private final String message;

        @JsonProperty("http_status_code")
        private final int httpStatusCode;

        private ProcessorFailureAsJson(ProcessorFailure processorFailure) {
            this.message = processorFailure.message();
            this.httpStatusCode = processorFailure.statusCode();
        }

        private String getMessage() {
            return message;
        }

        private int httpStatusCode() {
            return httpStatusCode;
        }
    }

    @Override
    public void afterPropertiesSet() {
        contextPath = reactable().contextPath() + '/';
    }

    @Override
    public String contextPath() {
        return contextPath;
    }

    @Override
    public Reactable reactable() {
        return api;
    }

    @Override
    protected void doStart() throws Exception {
        logger.info("API handler is now starting, preparing API context...");
        long startTime = System.currentTimeMillis(); // Get the start Time
        super.doStart();

        // Start resources before
        applicationContext.getBean(ResourceLifecycleManager.class).start();
        applicationContext.getBean(PolicyManager.class).start();
        applicationContext.getBean(GroupLifecyleManager.class).start();

        long endTime = System.currentTimeMillis(); // Get the end Time
        logger.info("API handler started in {} ms and now ready to accept requests on {}/*",
                (endTime - startTime), api.getProxy().getContextPath());
    }

    @Override
    protected void doStop() throws Exception {
        logger.info("API handler is now stopping, closing context...");

        applicationContext.getBean(PolicyManager.class).stop();
        applicationContext.getBean(ResourceLifecycleManager.class).stop();
        applicationContext.getBean(GroupLifecyleManager.class).stop();

        super.doStop();
        logger.info("API handler is now stopped", api);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ApiReactorHandler{");
        sb.append("contextPath=").append(api.getProxy().getContextPath());
        sb.append('}');
        return sb.toString();
    }
}
