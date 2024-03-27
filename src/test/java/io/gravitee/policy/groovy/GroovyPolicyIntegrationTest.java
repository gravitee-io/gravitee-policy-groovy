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
package io.gravitee.policy.groovy;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.configuration.GatewayConfigurationBuilder;
import io.gravitee.definition.model.Api;
import io.gravitee.definition.model.ExecutionMode;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.policy.groovy.configuration.GroovyPolicyConfiguration;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class GroovyPolicyIntegrationTest extends AbstractPolicyTest<GroovyPolicy, GroovyPolicyConfiguration> {

    @Override
    protected void configureGateway(GatewayConfigurationBuilder gatewayConfigurationBuilder) {
        super.configureGateway(gatewayConfigurationBuilder);
        gatewayConfigurationBuilder.set("api.jupiterMode.enabled", "false");
    }

    /**
     * Override api plans to have a published API_KEY one.
     * @param api is the api to apply this function code
     */
    @Override
    public void configureApi(Api api) {
        api.setExecutionMode(ExecutionMode.V3);
    }

    @Test
    @DeployApi("/apis/api.json")
    void should_execute_script(WebClient client) {
        wiremock.stubFor(post("/team").willReturn(ok("").withHeader("X-To-Remove", "value")));

        client
            .post("/test")
            .rxSend()
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertValue(
                response -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().names()).doesNotContain("X-To-Remove");
                    return true;
                }
            )
            .assertComplete()
            .assertNoErrors();

        wiremock.verify(1, postRequestedFor(urlPathEqualTo("/team")).withHeader("X-Gravitee-Groovy", equalTo("Yes")));
    }

    @Test
    @DeployApi("/apis/api-fail-response-template-no-key.json")
    void should_not_use_response_template_when_no_key_provided(WebClient client) {
        wiremock.stubFor(post("/team").willReturn(ok("")));

        client
            .post("/test")
            .putHeader(HttpHeaderNames.ACCEPT.toString(), "*/*")
            .putHeader("X-Gravitee-Break", "break")
            .rxSend()
            .map(
                response -> {
                    assertThat(response.statusCode()).isEqualTo(409);
                    assertThat(response.headers().get(HttpHeaderNames.CONTENT_TYPE)).isNotNull().isEqualTo("application/json");
                    return response.body();
                }
            )
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertValue(
                body -> {
                    assertThat(body).hasToString("{\"message\":\"Error message no response template\",\"http_status_code\":409}");
                    return true;
                }
            )
            .assertComplete()
            .assertNoErrors();

        wiremock.verify(0, postRequestedFor(urlPathEqualTo("/team")));
    }

    @Test
    @DeployApi("/apis/api-fail-response-template.json")
    void should_use_response_template_when_key_provided(WebClient client) {
        wiremock.stubFor(post("/team").willReturn(ok("")));

        client
            .post("/test")
            .putHeader(HttpHeaderNames.ACCEPT.toString(), "*/*")
            .putHeader("X-Gravitee-Break", "break")
            .rxSend()
            .map(
                response -> {
                    assertThat(response.statusCode()).isEqualTo(450);
                    assertThat(response.headers().get(HttpHeaderNames.CONTENT_TYPE)).isNotNull().isEqualTo("application/xml");
                    return response.body();
                }
            )
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertValue(
                body -> {
                    assertThat(body)
                        .hasToString(
                            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<auth>\n    <resp>\n        <hdr>E</hdr>\n        <errDesc>internal technical error </errDesc>\n    </resp>\n</auth>"
                        );
                    return true;
                }
            )
            .assertComplete()
            .assertNoErrors();

        wiremock.verify(0, postRequestedFor(urlPathEqualTo("/team")));
    }

    @Test
    @DeployApi("/apis/api-fail-parse-response-content.json")
    void should_throw_exception_on_response_content(WebClient client) {
        wiremock.stubFor(post("/team").willReturn(ok("[{\"foo\", \"bar\"},\"bar\": \"baz\"]")));

        client
            .post("/test")
            .rxSend()
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertValue(
                response -> {
                    assertThat(response.statusCode()).isEqualTo(500);
                    assertThat(response.bodyAsString()).contains("Internal Server Error");
                    return true;
                }
            )
            .assertComplete()
            .assertNoErrors();
    }

    @Test
    @DeployApi("/apis/api-fail-parse-request-content.json")
    void should_throw_exception_on_request_content(WebClient client) {
        wiremock.stubFor(post("/team").willReturn(ok("")));

        client
            .post("/test")
            .rxSendJson("[{\"foo\": \"bar\"}, {\"bar\": \"baz\"}]")
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertValue(
                response -> {
                    assertThat(response.statusCode()).isEqualTo(500);
                    assertThat(response.bodyAsString()).contains("Internal Server Error");
                    return true;
                }
            )
            .assertComplete()
            .assertNoErrors();
    }

    @Test
    @DeployApi("/apis/api-fail-execute-response-script.json")
    void should_throw_exception_on_response(WebClient client) {
        wiremock.stubFor(post("/team").willReturn(ok("")));

        client
            .post("/test")
            .rxSend()
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertValue(
                response -> {
                    assertThat(response.statusCode()).isEqualTo(500);
                    assertThat(response.bodyAsString()).contains("Internal Server Error");
                    return true;
                }
            )
            .assertComplete()
            .assertNoErrors();
    }

    @Test
    @DeployApi("/apis/api-fail-execute-request-script.json")
    void should_throw_exception_on_request(WebClient client) {
        wiremock.stubFor(post("/team").willReturn(ok("")));

        client
            .post("/test")
            .rxSend()
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertValue(
                response -> {
                    assertThat(response.statusCode()).isEqualTo(500);
                    assertThat(response.bodyAsString()).contains("Internal Server Error");
                    return true;
                }
            )
            .assertComplete()
            .assertNoErrors();
    }
}
