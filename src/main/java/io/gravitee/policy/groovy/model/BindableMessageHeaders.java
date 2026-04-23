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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Declares {@code putAt}/{@code getAt} so Groovy subscript assignment resolves here before falling
 * through to DGM's {@code putAt(Map, Object, Object)}, which would cast-fail on the
 * {@code Map<String, List<String>>} bridge method for non-{@link List} values.
 */
public abstract class BindableMessageHeaders implements Map<String, List<String>> {

    @Override
    public boolean containsValue(Object value) {
        throw new IllegalStateException();
    }

    @SuppressWarnings("unused")
    public List<String> put(String key, String value) {
        return put(key, List.of(value));
    }

    @SuppressWarnings("unused")
    public List<String> putAt(String key, String value) {
        return put(key, value);
    }

    @SuppressWarnings("unused")
    public List<String> putAt(String key, List<String> value) {
        return put(key, value);
    }

    @SuppressWarnings("unused")
    public List<String> putAt(String key, Object value) {
        if (value == null) {
            return remove(key);
        }
        if (value instanceof List<?> list) {
            List<String> stringList = new ArrayList<>(list.size());
            for (Object element : list) {
                stringList.add(element == null ? null : element.toString());
            }
            return put(key, stringList);
        }
        return put(key, value.toString());
    }

    @SuppressWarnings("unused")
    public List<String> getAt(String key) {
        return get(key);
    }

    @Override
    public void putAll(@Nonnull Map<? extends String, ? extends List<String>> all) {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
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
