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
package io.gravitee.policy.groovy.model;

import io.gravitee.gateway.api.http.HttpHeaders;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BindableHttpHeaders implements Map<String, List<String>> {

    private final HttpHeaders headers;

    public BindableHttpHeaders(HttpHeaders headers) {
        this.headers = headers;
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return headers.contains((String) key);
    }

    @Override
    public boolean containsValue(Object value) {
        throw new IllegalStateException();
    }

    @Override
    public List<String> get(Object key) {
        return headers.getAll((String) key);
    }

    @SuppressWarnings("unused")
    public List<String> put(String key, String value) {
        List<String> oldValue = get(key);
        headers.set(key, List.of(value));
        return oldValue;
    }

    @Override
    public List<String> put(String key, List<String> value) {
        List<String> oldValue = get(key);
        headers.set(key, new ArrayList<>(value));
        return oldValue;
    }

    @Override
    public List<String> remove(Object key) {
        List<String> oldValue = get(key);
        headers.remove((String) key);
        return oldValue;
    }

    @Override
    public void putAll(@Nonnull Map<? extends String, ? extends List<String>> all) {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public void clear() {
        headers.clear();
    }

    @Override
    public Set<String> keySet() {
        return headers.names();
    }

    @Override
    public Collection<List<String>> values() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public Set<Entry<String, List<String>>> entrySet() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }
}
