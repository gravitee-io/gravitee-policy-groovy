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
package io.gravitee.policy.groovy.model.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.context.HttpRequest;
import io.gravitee.gateway.reactive.api.context.HttpResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * @author GraviteeSource Team
 */
class BindableHttpContentTest {

    private static final byte[] PNG_BYTES = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, (byte) 0x80, (byte) 0xFF };

    private static final String REQUEST_CONTENT_DISABLED = "Accessing request content must be enabled in the policy configuration";

    private static final String RESPONSE_CONTENT_DISABLED = "Accessing response content must be enabled in the policy configuration";

    @Test
    void should_expose_request_content_as_bytes_and_base64() {
        BindableHttpRequest request = new BindableHttpRequest(mock(HttpRequest.class), Buffer.buffer(PNG_BYTES));

        assertThat(request.getContentAsByteArray()).containsExactly(PNG_BYTES);
        assertThat(request.getContentAsBase64()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        assertThat(request.getContent()).isEqualTo(Buffer.buffer(PNG_BYTES).toString());
    }

    @Test
    void should_expose_response_content_as_bytes_and_base64() {
        BindableHttpResponse response = new BindableHttpResponse(mock(HttpResponse.class), Buffer.buffer(PNG_BYTES));

        assertThat(response.getContentAsByteArray()).containsExactly(PNG_BYTES);
        assertThat(response.getContentAsBase64()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        assertThat(response.getContent()).isEqualTo(Buffer.buffer(PNG_BYTES).toString());
    }

    @Test
    void should_expose_empty_request_content_as_empty_bytes() {
        BindableHttpRequest request = new BindableHttpRequest(mock(HttpRequest.class), Buffer.buffer());

        assertThat(request.getContentAsByteArray()).isEmpty();
        assertThat(request.getContentAsBase64()).isEmpty();
        assertThat(request.getContent()).isEmpty();
    }

    @Test
    void should_expose_empty_response_content_as_empty_bytes() {
        BindableHttpResponse response = new BindableHttpResponse(mock(HttpResponse.class), Buffer.buffer());

        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentAsBase64()).isEmpty();
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void should_reject_request_content_access_when_read_content_is_disabled() {
        BindableHttpRequest request = new BindableHttpRequest(mock(HttpRequest.class));

        assertThatThrownBy(request::getContent).isInstanceOf(UnsupportedOperationException.class).hasMessage(REQUEST_CONTENT_DISABLED);
        assertThatThrownBy(request::getContentAsByteArray)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage(REQUEST_CONTENT_DISABLED);
        assertThatThrownBy(request::getContentAsBase64)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage(REQUEST_CONTENT_DISABLED);
    }

    @Test
    void should_reject_response_content_access_when_read_content_is_disabled() {
        BindableHttpResponse response = new BindableHttpResponse(mock(HttpResponse.class));

        assertThatThrownBy(response::getContent).isInstanceOf(UnsupportedOperationException.class).hasMessage(RESPONSE_CONTENT_DISABLED);
        assertThatThrownBy(response::getContentAsByteArray)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage(RESPONSE_CONTENT_DISABLED);
        assertThatThrownBy(response::getContentAsBase64)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage(RESPONSE_CONTENT_DISABLED);
    }
}
