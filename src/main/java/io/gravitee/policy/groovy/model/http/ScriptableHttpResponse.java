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
import io.gravitee.policy.groovy.model.ScriptableHttpHeaders;
import lombok.Getter;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ScriptableHttpResponse implements GenericResponse {

    @Getter
    private final HttpResponse response;

    @Getter
    private final String content;

    public ScriptableHttpResponse(HttpResponse response, Buffer buffer) {
        this.response = response;
        this.content = buffer.toString();
    }

    public ScriptableHttpResponse(HttpResponse response) {
        this(response, Buffer.buffer());
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

    public ScriptableHttpHeaders getHeaders() {
        return new ScriptableHttpHeaders(headers());
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
