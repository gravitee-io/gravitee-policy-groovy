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
package io.gravitee.policy.groovy.model.message;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.policy.groovy.model.ScriptableHttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class ScriptableMessage implements Message {

    private final Message message;

    @Override
    public String id() {
        return message.id();
    }

    @Override
    public String correlationId() {
        return message.correlationId();
    }

    public String getCorrelationId() {
        return correlationId();
    }

    @Override
    public String parentCorrelationId() {
        return message.parentCorrelationId();
    }

    public String getParentCorrelationId() {
        return parentCorrelationId();
    }

    @Override
    public long timestamp() {
        return message.timestamp();
    }

    public long getTimestamp() {
        return timestamp();
    }

    @Override
    public boolean error() {
        return message.error();
    }

    public boolean isError() {
        return error();
    }

    @Override
    public Message error(boolean error) {
        return message.error(error);
    }

    @Override
    public Map<String, Object> metadata() {
        return message.metadata();
    }

    public Map<String, Object> getMetadata() {
        return metadata();
    }

    @Override
    public HttpHeaders headers() {
        return message.headers();
    }

    public ScriptableHttpHeaders getHeaders() {
        return new ScriptableHttpHeaders(headers());
    }

    @Override
    public Message headers(HttpHeaders headers) {
        return message.headers(headers);
    }

    @Override
    public Buffer content() {
        return message.content();
    }

    public String getContent() {
        return message.content() == null ? "" : content().toString();
    }

    @Override
    public Message content(Buffer content) {
        throw new UnsupportedOperationException("Setting content must be done returning a value and setting `overrideContent` to true");
    }

    @Override
    public Message content(String content) {
        throw new UnsupportedOperationException("Setting content must be done returning a value and setting `overrideContent` to true");
    }

    @Override
    public void ack() {
        message.ack();
    }

    @Override
    public <T> T attribute(String name) {
        return message.attribute(name);
    }

    public <T> T getAttribute(String name) {
        return attribute(name);
    }

    @Override
    public <T> List<T> attributeAsList(String name) {
        return message.attributeAsList(name);
    }

    public <T> List<T> getAttributeAsList(String name) {
        return attributeAsList(name);
    }

    @Override
    public Message attribute(String name, Object value) {
        return message.attribute(name, value);
    }

    @Override
    public Message removeAttribute(String name) {
        return message.removeAttribute(name);
    }

    @Override
    public Set<String> attributeNames() {
        return message.attributeNames();
    }

    @Override
    public <T> Map<String, T> attributes() {
        return message.attributes();
    }

    public Map<String, Object> getAttributes() {
        return new ScriptableMessageAttributes(message);
    }

    @Override
    public <T> T internalAttribute(String name) {
        return message.internalAttribute(name);
    }

    @Override
    public Message internalAttribute(String name, Object value) {
        return message.internalAttribute(name, value);
    }

    @Override
    public Message removeInternalAttribute(String name) {
        return message.removeInternalAttribute(name);
    }

    @Override
    public Set<String> internalAttributeNames() {
        return message.internalAttributeNames();
    }

    @Override
    public <T> Map<String, T> internalAttributes() {
        return message.internalAttributes();
    }
}
