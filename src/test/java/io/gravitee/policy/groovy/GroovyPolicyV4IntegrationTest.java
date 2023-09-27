/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.groovy;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

import com.graviteesource.entrypoint.http.get.HttpGetEntrypointConnectorFactory;
import com.graviteesource.entrypoint.http.post.HttpPostEntrypointConnectorFactory;
import com.graviteesource.reactor.message.MessageApiReactorFactory;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.MessageStorage;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.PersistentMockEndpointConnectorFactory;
import io.gravitee.apim.gateway.tests.sdk.reactor.ReactorBuilder;
import io.gravitee.apim.plugin.reactor.ReactorPlugin;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.reactive.reactor.v4.reactor.ReactorFactory;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.endpoint.mock.MockEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.gravitee.policy.groovy.configuration.GroovyPolicyConfiguration;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.core.http.HttpClientResponse;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroovyPolicyV4IntegrationTest {

    private static final JsonObject GIVEN_CONTENT = new JsonObject("""
        {"message":"Hello World!"}
        """);

    private static final JsonObject EXPECTED_CONTENT = new JsonObject("""
        {"message":"Hello Universe!"}
        """);

    @GatewayTest
    @Nested
    class HttpProxyTest extends AbstractPolicyTest<GroovyPolicy, GroovyPolicyConfiguration> {

        @Override
        public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
            entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
        }

        @Override
        public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
            endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
        }

        @Test
        @DeployApi("/apis/v4/api-request.json")
        void should_override_content_on_request_script(HttpClient client) {
            wiremock.stubFor(post("/team").willReturn(ok("")));

            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(GIVEN_CONTENT.toString()))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/team"))
                    .withHeader("X-Phase", equalTo("on-request"))
                    .withRequestBody(equalToJson(EXPECTED_CONTENT.toString()))
            );
        }

        @Test
        @DeployApi("/apis/v4/api-request-no-content-override.json")
        void should_not_override_content_on_request_script(HttpClient client) {
            wiremock.stubFor(post("/team").willReturn(ok("")));

            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(GIVEN_CONTENT.toString()))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/team"))
                    .withHeader("X-Phase", equalTo("on-request"))
                    .withRequestBody(equalToJson(GIVEN_CONTENT.toString()))
            );
        }

        @Test
        @DeployApi("/apis/v4/api-request-unsupported-content-access.json")
        void should_fail_on_request_with_read_content_disabled(HttpClient client) {
            wiremock.stubFor(post("/team").willReturn(ok("")));

            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(GIVEN_CONTENT.toString()))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(500))
                .flatMap(HttpClientResponse::body)
                .doOnSuccess(body -> assertThat(body).hasToString("Internal Server Error"))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete();

            wiremock.verify(0, anyRequestedFor(anyUrl()));
        }

        @Test
        @DeployApi("/apis/v4/api-response.json")
        void should_override_content_on_response_script(HttpClient client) {
            wiremock.stubFor(post("/team").willReturn(okJson(GIVEN_CONTENT.toString())));

            client
                .rxRequest(POST, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .doOnSuccess(response -> assertThat(response.headers().names()).contains("X-Phase"))
                .flatMap(HttpClientResponse::body)
                .doOnSuccess(body -> assertThat(body).hasToString(EXPECTED_CONTENT.toString()))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(1, postRequestedFor(urlPathEqualTo("/team")));
        }

        @Test
        @DeployApi("/apis/v4/api-response-no-content-override.json")
        void should_not_override_content_on_response_script(HttpClient client) {
            wiremock.stubFor(post("/team").willReturn(okJson(GIVEN_CONTENT.toString())));

            client
                .rxRequest(POST, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .doOnSuccess(response -> assertThat(response.headers().names()).contains("X-Phase"))
                .flatMap(HttpClientResponse::body)
                .doOnSuccess(body -> assertThat(body).hasToString(GIVEN_CONTENT.toString()))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(1, postRequestedFor(urlPathEqualTo("/team")));
        }

        @Test
        @DeployApi("/apis/v4/api-response-unsupported-content-access.json")
        void should_fail_on_response_with_read_content_disabled(HttpClient client) {
            wiremock.stubFor(post("/team").willReturn(okJson(EXPECTED_CONTENT.toString())));

            client
                .rxRequest(POST, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(500))
                .flatMap(HttpClientResponse::body)
                .doOnSuccess(body -> assertThat(body).hasToString("Internal Server Error"))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete();

            wiremock.verify(1, postRequestedFor(urlPathEqualTo("/team")));
        }
    }

    @GatewayTest
    @Nested
    class SubscribeTest extends AbstractPolicyTest<GroovyPolicy, GroovyPolicyConfiguration> {

        @Override
        public void configureReactors(Set<ReactorPlugin<? extends ReactorFactory<?>>> reactors) {
            reactors.add(ReactorBuilder.build(MessageApiReactorFactory.class));
        }

        @Override
        public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
            entrypoints.putIfAbsent("http-get", EntrypointBuilder.build("http-get", HttpGetEntrypointConnectorFactory.class));
        }

        @Override
        public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
            endpoints.putIfAbsent("mock", EndpointBuilder.build("mock", MockEndpointConnectorFactory.class));
        }

        @Test
        @DeployApi("/apis/v4/api-subscribe.json")
        void should_execute_on_response_message_script(HttpClient client) {
            client
                .rxRequest(GET, "/test")
                .map(request -> request.putHeader(HttpHeaderNames.ACCEPT, MediaType.APPLICATION_JSON))
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .flatMap(HttpClientResponse::body)
                .map(Buffer::toString)
                .map(json -> new JsonObject(json).getJsonArray("items"))
                .doOnSuccess(items -> assertThat(items).hasSize(2))
                .doOnSuccess(items -> {
                    items.forEach(item -> {
                        var message = (JsonObject) item;
                        assertThat(message.getString("content")).isEqualTo(EXPECTED_CONTENT.toString());
                        var headers = message.getJsonObject("headers");
                        assertThat(headers.getString("x-phase")).contains("on-response-message");
                    });
                })
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete();
        }
    }

    @GatewayTest
    @Nested
    class PublishTest extends AbstractPolicyTest<GroovyPolicy, GroovyPolicyConfiguration> {

        private MessageStorage messageStorage;

        @Override
        public void configureReactors(Set<ReactorPlugin<? extends ReactorFactory<?>>> reactors) {
            reactors.add(ReactorBuilder.build(MessageApiReactorFactory.class));
        }

        @Override
        public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
            entrypoints.putIfAbsent("http-post", EntrypointBuilder.build("http-post", HttpPostEntrypointConnectorFactory.class));
        }

        @Override
        public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
            endpoints.putIfAbsent("mock", EndpointBuilder.build("mock", PersistentMockEndpointConnectorFactory.class));
        }

        @BeforeEach
        void setUp() {
            messageStorage = getBean(MessageStorage.class);
        }

        @AfterEach
        void tearDown() {
            messageStorage.reset();
        }

        @Test
        @DeployApi("/apis/v4/api-publish.json")
        void should_transform_on_request_message(HttpClient client) {
            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(GIVEN_CONTENT.toString()))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(202))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete();

            messageStorage
                .subject()
                .doOnNext(message -> assertThat(message.content()).hasToString(EXPECTED_CONTENT.toString()))
                .doOnNext(message -> assertThat(message.attributes()).containsEntry("message.mutated", true))
                .test()
                .assertNoErrors()
                .dispose();
        }

        @Test
        @DeployApi("/apis/v4/api-publish-with-script-error.json")
        void should_return_error_500_if_script_fails(HttpClient client) {
            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(GIVEN_CONTENT.toString()))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(500))
                .flatMap(HttpClientResponse::body)
                .doOnSuccess(body -> assertThat(body).hasToString("Internal Server Error"))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete();

            messageStorage.subject().test().assertNoValues().dispose();
        }
    }
}
