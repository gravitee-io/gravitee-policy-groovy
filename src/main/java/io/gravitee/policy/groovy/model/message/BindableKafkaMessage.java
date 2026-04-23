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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BindableKafkaMessage extends BindableMessage {

    private final KafkaMessage kafkaMessage;

    public BindableKafkaMessage(KafkaMessage kafkaMessage) {
        super(kafkaMessage);
        this.kafkaMessage = kafkaMessage;
    }

    @Override
    public BindableKafkaRecordHeaders getHeaders() {
        return new BindableKafkaRecordHeaders(kafkaMessage);
    }

    /**
     * Returns the message key as a string. Use {@link #getKeyAsBuffer()} for raw binary access.
     */
    public String getKey() {
        Buffer key = kafkaMessage.key();
        return key == null ? null : key.toString();
    }

    /**
     * Returns the message key as a raw {@link Buffer} for binary access.
     */
    public Buffer getKeyAsBuffer() {
        return kafkaMessage.key();
    }

    public String getTopic() {
        return kafkaMessage.topic();
    }

    public long getOffset() {
        return kafkaMessage.offset();
    }

    public int getSequence() {
        return kafkaMessage.sequence();
    }

    public int getPartition() {
        return kafkaMessage.indexPartition();
    }

    public int getSizeInBytes() {
        return kafkaMessage.sizeInBytes();
    }

    /**
     * Returns Kafka record headers with raw {@link Buffer} values.
     * Use {@link #getRecordHeadersAsStrings()} for string-valued headers.
     */
    public Map<String, Buffer> getRecordHeaders() {
        return kafkaMessage.recordHeaders();
    }

    /**
     * Returns Kafka record headers with values converted to strings.
     */
    public Map<String, String> getRecordHeadersAsStrings() {
        return kafkaMessage
            .recordHeaders()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() == null ? null : e.getValue().toString()));
    }
}
