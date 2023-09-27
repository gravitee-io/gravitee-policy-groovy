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

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.context.GenericResponse;
import io.gravitee.gateway.reactive.api.context.HttpResponse;
import io.gravitee.policy.groovy.model.BindableHttpHeaders;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BindableHttpResponse implements GenericResponse {

    @Getter
    private final HttpResponse response;

    private final String content;

    public BindableHttpResponse(HttpResponse response, @Nullable Buffer buffer) {
        this.response = response;
        this.content = buffer != null ? buffer.toString() : null;
    }

    public BindableHttpResponse(HttpResponse response) {
        this(response, null);
    }

    public String getContent() {
        if (content == null) {
            throw new UnsupportedOperationException("Accessing response content must be enabled in the policy configuration");
        }
        return content;
    }

    @Override
    public GenericResponse status(int httpStatusCode) {
        return response.status(httpStatusCode);
    }

    @Override
    public int status() {
        return response.status();
    }

    public int getStatus() {
        return status();
    }

    @Override
    public String reason() {
        return response.reason();
    }

    public String getReason() {
        return reason();
    }

    @Override
    public GenericResponse reason(String reason) {
        return response.reason(reason);
    }

    @Override
    public HttpHeaders headers() {
        return response.headers();
    }

    public BindableHttpHeaders getHeaders() {
        return new BindableHttpHeaders(headers());
    }

    @Override
    public HttpHeaders trailers() {
        return response.trailers();
    }

    @Override
    public boolean ended() {
        return response.ended();
    }
}
