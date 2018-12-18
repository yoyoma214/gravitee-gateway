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
package io.gravitee.gateway.core.processor.chain;

import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.stream.ReadStream;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.WriteStream;
import io.gravitee.gateway.core.processor.ProcessorFailure;
import io.gravitee.gateway.core.processor.StreamableProcessor;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractStreamableProcessorChain<T, S, H extends StreamableProcessor<T, S>> extends AbstractProcessorChain<T, H, H>
        implements StreamableProcessorChain<T, S, H> {

    private H streamableProcessorChain;
    protected Handler<ProcessorFailure> streamErrorHandler;
    private boolean initialized;

    @Override
    public void handle(T data) {
        if (! initialized) {
            prepareStreamableProcessors(data);
            initialized = true;
            //TODO: trouver une autre façon de réinitialiser l'iterateur pour lancer les processors
            reset();
        }

        if (hasNext()) {
            H processor = next(data);

            processor
                    .handler(__ -> handle(data))
                    .errorHandler(failure -> errorHandler.handle(failure))
                    .exitHandler(stream -> exitHandler.handle(null))
                    .streamErrorHandler(failure -> streamErrorHandler.handle(failure))
                    .handle(data);
        } else {
            doOnSuccess(data);
        }
    }

    private void prepareStreamableProcessors(T data) {
        final ReadWriteStream<S>[] previousProcessor = new ReadWriteStream[]{null};

        while(hasNext()) {
            H processor = next(data);
            System.out.println("Stream processor: " + processor);
            if (streamableProcessorChain == null) {
                streamableProcessorChain = processor;
            }

            // Chain policy stream using the previous one
            if (previousProcessor[0] != null) {
                previousProcessor[0].bodyHandler(processor::write);
                previousProcessor[0].endHandler(result1 -> processor.end());
            }

            // Previous stream is now the current policy stream
            previousProcessor[0] = processor;
        }

        ReadWriteStream<S> tailPolicyStreamer = previousProcessor[0];
        if (streamableProcessorChain != null && tailPolicyStreamer != null) {
            tailPolicyStreamer.bodyHandler(bodyPart -> {if (bodyHandler != null) bodyHandler.handle(bodyPart);});
            tailPolicyStreamer.endHandler(result -> {if (endHandler != null) endHandler.handle(result);});
        }
    }

    private Handler<S> bodyHandler;

    @Override
    public ReadStream<S> bodyHandler(Handler<S> handler) {
        this.bodyHandler = handler;
        return this;
    }

    private Handler<Void> endHandler;

    @Override
    public ReadStream<S> endHandler(Handler<Void> handler) {
        this.endHandler = handler;
        return this;
    }

    @Override
    public WriteStream<S> write(S chunk) {
        if (streamableProcessorChain != null) {
            streamableProcessorChain.write(chunk);
        } else {
            this.bodyHandler.handle(chunk);
        }

        return this;
    }

    @Override
    public void end() {
        if (streamableProcessorChain != null) {
            streamableProcessorChain.end();
        } else if (endHandler != null) {
            this.endHandler.handle(null);
        }
    }

    @Override
    public StreamableProcessorChain<T, S, H> handler(Handler<H> handler) {
        this.resultHandler = handler;
        return this;
    }

    @Override
    public StreamableProcessorChain<T, S, H> errorHandler(Handler<ProcessorFailure> errorHandler) {
        this.errorHandler = errorHandler;
        return this;
    }

    @Override
    public StreamableProcessorChain<T, S, H> exitHandler(Handler<Void> exitHandler) {
        this.exitHandler = exitHandler;
        return this;
    }

    @Override
    public StreamableProcessorChain<T, S, H> streamErrorHandler(Handler<ProcessorFailure> handler) {
        this.streamErrorHandler = handler;
        return this;
    }

    @Override
    public void doOnSuccess(T data) {
        resultHandler.handle(streamableProcessorChain);
    }

    protected abstract void reset();
}
