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
package io.gravitee.gateway.standalone;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.definition.model.Endpoint;
import io.gravitee.gateway.handlers.api.definition.Api;
import io.gravitee.gateway.standalone.junit.annotation.ApiDescriptor;
import io.gravitee.gateway.standalone.junit.rules.ApiDeployer;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.net.URL;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ApiDescriptor("/io/gravitee/gateway/standalone/teams.json")
public class SimpleGatewayTest extends AbstractGatewayTest {

    private WireMockRule wireMockRule = new WireMockRule(
            wireMockConfig()
                    .notifier(new Slf4jNotifier(true))
                    .dynamicPort());

    @Rule
    public final TestRule chain = RuleChain
            .outerRule(wireMockRule)
            .around(new ApiDeployer(this));

    @Test
    public void call_get_started_api() throws Exception {
        stubFor(get(urlEqualTo("/team/my_team"))
                .willReturn(
                        aResponse()
                                .withStatus(HttpStatus.SC_OK)));

        Request request = Request.Get("http://localhost:8082/test/my_team");
        Response response = request.execute();
        HttpResponse returnResponse = response.returnResponse();

        assertEquals(HttpStatus.SC_OK, returnResponse.getStatusLine().getStatusCode());
    }

    @Override
    public void before(Api api) {
        super.before(api);

        try {
            Endpoint edpt = api.getProxy().getGroups().iterator().next().getEndpoints().iterator().next();
            URL target = new URL(edpt.getTarget());
            URL newTarget = new URL(target.getProtocol(), target.getHost(), wireMockRule.port(), target.getFile());
            edpt.setTarget(newTarget.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
