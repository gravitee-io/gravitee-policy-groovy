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

import io.gravitee.gateway.reactive.api.message.Message;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BindableMessageAttributes implements Map<String, Object> {

    private final Message message;
    private final Map<String, Object> attributes;

    public BindableMessageAttributes(Message message) {
        this.message = message;
        this.attributes = message.attributes();
    }

    @Override
    public int size() {
        return attributes.size();
    }

    @Override
    public boolean isEmpty() {
        return attributes.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return attributes.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return attributes.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return attributes.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        return message.attribute(key, value).attribute(key);
    }

    @Override
    public Object remove(Object key) {
        var old = get(key);
        message.removeAttribute((String) key);
        return old;
    }

    @Override
    public void putAll(@Nonnull Map<? extends String, ?> all) {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public Set<String> keySet() {
        return message.attributeNames();
    }

    @Override
    public Collection<Object> values() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }
}
