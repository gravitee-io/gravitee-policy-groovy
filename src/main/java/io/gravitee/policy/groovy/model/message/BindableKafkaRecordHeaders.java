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
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.policy.groovy.model.BindableMessageHeaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Replaces the parent's HTTP-backed wrapper because {@link KafkaMessage#headers()} throws.
 * Record headers are single-valued {@link Buffer}s, so list-valued writes keep the last element.
 */
public class BindableKafkaRecordHeaders extends BindableMessageHeaders {

    private final KafkaMessage kafkaMessage;

    public BindableKafkaRecordHeaders(KafkaMessage kafkaMessage) {
        this.kafkaMessage = kafkaMessage;
    }

    @Override
    public int size() {
        return kafkaMessage.recordHeaders().size();
    }

    @Override
    public boolean isEmpty() {
        return kafkaMessage.recordHeaders().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return kafkaMessage.recordHeaders().containsKey((String) key);
    }

    @Override
    public List<String> get(Object key) {
        Buffer value = kafkaMessage.recordHeaders().get((String) key);
        if (value == null) {
            return null;
        }
        List<String> wrapped = new ArrayList<>(1);
        wrapped.add(value.toString());
        return wrapped;
    }

    @Override
    public List<String> put(String key, List<String> value) {
        List<String> oldValue = get(key);
        if (value == null || value.isEmpty()) {
            kafkaMessage.removeRecordHeader(key);
            return oldValue;
        }
        String last = value.get(value.size() - 1);
        if (last == null) {
            kafkaMessage.removeRecordHeader(key);
        } else {
            kafkaMessage.putRecordHeader(key, Buffer.buffer(last));
        }
        return oldValue;
    }

    @Override
    public List<String> remove(Object key) {
        List<String> oldValue = get(key);
        kafkaMessage.removeRecordHeader((String) key);
        return oldValue;
    }

    @Override
    public void clear() {
        for (String name : new ArrayList<>(kafkaMessage.recordHeaders().keySet())) {
            kafkaMessage.removeRecordHeader(name);
        }
    }

    @Override
    public Set<String> keySet() {
        return kafkaMessage.recordHeaders().keySet();
    }
}
