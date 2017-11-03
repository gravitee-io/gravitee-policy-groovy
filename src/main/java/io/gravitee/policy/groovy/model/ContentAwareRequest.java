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
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.stream.ReadStream;
import io.gravitee.reporter.api.http.Metrics;

import java.time.Instant;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ContentAwareRequest implements Request {

    private final Request request;
    private final String content;

    public ContentAwareRequest(Request request, String content) {
        this.request = request;
        this.content = content;
    }

    public Request getRequest() {
        return request;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String id() {
        return request.id();
    }

    public String getId() {
        return this.id();
    }

    @Override
    public String transactionId() {
        return request.transactionId();
    }

    public String getYransactionId() {
        return this.transactionId();
    }

    @Override
    public String uri() {
        return request.uri();
    }

    public String getUri() {
        return this.uri();
    }

    @Override
    public String path() {
        return request.path();
    }

    public String getPath() {
        return this.path();
    }

    @Override
    public String pathInfo() {
        return request.pathInfo();
    }

    public String getPathInfo() {
        return this.pathInfo();
    }

    @Override
    public String contextPath() {
        return request.contextPath();
    }

    public String getContextPath() {
        return this.contextPath();
    }

    @Override
    public MultiValueMap<String, String> parameters() {
        return request.parameters();
    }

    public MultiValueMap<String, String> getParameters() {
        return this.parameters();
    }

    @Override
    public HttpHeaders headers() {
        return request.headers();
    }

    public HttpHeaders getHeaders() {
        return this.headers();
    }

    @Override
    public HttpMethod method() {
        return request.method();
    }

    public HttpMethod getMethod() {
        return this.method();
    }

    @Override
    public HttpVersion version() {
        return request.version();
    }

    public HttpVersion getVersion() {
        return this.version();
    }

    @Override
    public Instant timestamp() {
        return request.timestamp();
    }

    public Instant getTimestamp() {
        return this.timestamp();
    }

    @Override
    public String remoteAddress() {
        return request.remoteAddress();
    }

    public String getRemoteAddress() {
        return this.remoteAddress();
    }

    @Override
    public String localAddress() {
        return request.localAddress();
    }

    public String getLocalAddress() {
        return this.localAddress();
    }

    @Override
    public Metrics metrics() {
        return request.metrics();
    }

    @Override
    public ReadStream<Buffer> bodyHandler(Handler<Buffer> bodyHandler) {
        return request.bodyHandler(bodyHandler);
    }

    @Override
    public ReadStream<Buffer> endHandler(Handler<Void> endHandler) {
        return request.endHandler(endHandler);
    }
}
