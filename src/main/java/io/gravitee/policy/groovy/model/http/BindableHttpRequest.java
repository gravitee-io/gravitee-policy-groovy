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

import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.context.GenericRequest;
import io.gravitee.gateway.reactive.api.context.HttpRequest;
import io.gravitee.policy.groovy.model.BindableHttpHeaders;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;
import lombok.Getter;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BindableHttpRequest implements GenericRequest {

    @Getter
    private final HttpRequest request;

    private final String content;

    public BindableHttpRequest(HttpRequest request, @Nullable Buffer buffer) {
        this.request = request;
        this.content = buffer != null ? buffer.toString() : null;
    }

    public BindableHttpRequest(HttpRequest request) {
        this(request, null);
    }

    public String getContent() {
        if (content == null) {
            throw new UnsupportedOperationException("Accessing request content must be enabled in the policy configuration");
        }
        return content;
    }

    @Override
    public String id() {
        return request.id();
    }

    @Override
    public String transactionId() {
        return request.transactionId();
    }

    public String getTransactionId() {
        return transactionId();
    }

    @Override
    public String clientIdentifier() {
        return request.clientIdentifier();
    }

    public String getClientIdentifier() {
        return clientIdentifier();
    }

    @Override
    public String uri() {
        return request.uri();
    }

    public String getUri() {
        return uri();
    }

    @Override
    public String host() {
        return request.host();
    }

    public String getHost() {
        return host();
    }

    @Override
    public String originalHost() {
        return request.originalHost();
    }

    public String getOriginalHost() {
        return originalHost();
    }

    @Override
    public String path() {
        return request.path();
    }

    public String getPath() {
        return path();
    }

    @Override
    public String pathInfo() {
        return request.pathInfo();
    }

    public String getPathInfo() {
        return pathInfo();
    }

    @Override
    public String contextPath() {
        return request.contextPath();
    }

    public String getContextPath() {
        return contextPath();
    }

    @Override
    public MultiValueMap<String, String> parameters() {
        return request.parameters();
    }

    public MultiValueMap<String, String> getParameters() {
        return parameters();
    }

    @Override
    public MultiValueMap<String, String> pathParameters() {
        return request.pathParameters();
    }

    public MultiValueMap<String, String> getPathParameters() {
        return pathParameters();
    }

    @Override
    public HttpHeaders headers() {
        return request.headers();
    }

    public BindableHttpHeaders getHeaders() {
        return new BindableHttpHeaders(request.headers());
    }

    @Override
    public HttpMethod method() {
        return request.method();
    }

    public HttpMethod getMethod() {
        return method();
    }

    @Override
    public String scheme() {
        return request.scheme();
    }

    public String getScheme() {
        return scheme();
    }

    @Override
    public HttpVersion version() {
        return request.version();
    }

    public HttpVersion getVersion() {
        return version();
    }

    @Override
    public long timestamp() {
        return request.timestamp();
    }

    public long getTimestamp() {
        return timestamp();
    }

    @Override
    public String remoteAddress() {
        return request.remoteAddress();
    }

    public String getRemoteAddress() {
        return remoteAddress();
    }

    @Override
    public String localAddress() {
        return request.localAddress();
    }

    public String getLocalAddress() {
        return localAddress();
    }

    @Override
    public SSLSession sslSession() {
        return request.sslSession();
    }

    public SSLSession getSslSession() {
        return sslSession();
    }

    @Override
    public boolean ended() {
        return request.ended();
    }
}
