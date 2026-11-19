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

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.policy.PolicyBuilder;
import io.gravitee.definition.model.ExecutionMode;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.plugin.core.api.PluginManifest;
import io.gravitee.plugin.policy.PolicyPlugin;
import io.gravitee.policy.groovy.configuration.GroovyPolicyConfiguration;
import io.gravitee.policy.v3.groovy.GroovyPolicyV3IntegrationTest;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.core.http.HttpClientResponse;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest(v2ExecutionMode = ExecutionMode.V4_EMULATION_ENGINE)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class GroovyPolicyV4EmulationEngineIntegrationTest extends GroovyPolicyV3IntegrationTest {

    private static final byte[] PNG_BYTES = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, (byte) 0x80, (byte) 0xFF };

    /**
     * The parent suite's generic type is {@code GroovyPolicyV3}. Production loads {@link GroovyPolicy}, and the v4
     * emulation engine calls that reactive implementation directly because it implements {@code HttpPolicy}.
     */
    @Override
    public void loadPolicy(PluginManifest manifest, Map<String, PolicyPlugin> policies) {
        super.loadPolicy(manifest, policies);
        policies.put(policyName(), PolicyBuilder.build(policyName(), GroovyPolicy.class, GroovyPolicyConfiguration.class));
    }

    @Test
    @DeployApi("/apis/v3/api-post-binary.json")
    void should_return_binary_content_decoded_by_response_script(HttpClient client) {
        String encoded = Base64.getEncoder().encodeToString(PNG_BYTES);
        String envelope =
            "{\"statusCode\":201,\"headers\":{\"Content-Type\":\"image/png\"},\"body\":\"" + encoded + "\",\"isBase64Encoded\":true}";
        wiremock.stubFor(post("/team").willReturn(okJson(envelope)));

        client
            .rxRequest(POST, "/api-post-binary")
            .flatMap(HttpClientRequest::rxSend)
            .doOnSuccess(response -> {
                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.getHeader(HttpHeaderNames.CONTENT_TYPE)).isEqualTo("image/png");
                assertThat(response.getHeader(HttpHeaderNames.CONTENT_LENGTH)).isEqualTo(Integer.toString(PNG_BYTES.length));
            })
            .flatMap(HttpClientResponse::body)
            .doOnSuccess(body -> assertThat(body.getBytes()).containsExactly(PNG_BYTES))
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertComplete()
            .assertNoErrors();
    }
}
