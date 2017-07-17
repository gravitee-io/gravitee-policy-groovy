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
package io.gravitee.policy.groovy.model;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.stream.WriteStream;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ContentAwareResponse implements Response {

    private final Response response;
    private final String content;

    public ContentAwareResponse(Response response, String content) {
        this.response = response;
        this.content = content;
    }

    @Override
    public Response status(int statusCode) {
        return response.status(statusCode);
    }

    @Override
    public int status() {
        return response.status();
    }

    public int getStatus() {
        return this.status();
    }

    @Override
    public HttpHeaders headers() {
        return response.headers();
    }

    public HttpHeaders getHeaders() {
        return this.headers();
    }

    @Override
    public WriteStream<Buffer> write(Buffer content) {
        return response.write(content);
    }

    @Override
    public void end() {
        response.end();
    }

    public String getContent() {
        return content;
    }
}
