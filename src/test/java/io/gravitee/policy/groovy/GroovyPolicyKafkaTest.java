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
package io.gravitee.policy.groovy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageRequest;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageResponse;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.policy.groovy.configuration.GroovyPolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.kafka.common.protocol.Errors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class GroovyPolicyKafkaTest {

    @Mock
    private KafkaExecutionContext kafkaExecutionContext;

    @Mock
    private KafkaMessageExecutionContext kafkaMessageExecutionContext;

    @Mock
    private KafkaMessageRequest kafkaMessageRequest;

    @Mock
    private KafkaMessageResponse kafkaMessageResponse;

    @Captor
    private ArgumentCaptor<Function<KafkaMessage, Maybe<KafkaMessage>>> onMessageCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(kafkaMessageExecutionContext.executionContext()).thenReturn(kafkaExecutionContext);
        lenient().when(kafkaMessageExecutionContext.request()).thenReturn(kafkaMessageRequest);
        lenient().when(kafkaMessageExecutionContext.response()).thenReturn(kafkaMessageResponse);
        lenient()
            .when(kafkaExecutionContext.interruptWith(any(Errors.class)))
            .thenReturn(Completable.error(new RuntimeException("Kafka interrupt: UNKNOWN_SERVER_ERROR")));
    }

    @Nested
    class OnKafkaRequest {

        @Test
        void should_complete_when_no_script() {
            var policy = new GroovyPolicy(buildConfig(null));
            policy.onRequest(kafkaExecutionContext).test().assertComplete().assertNoErrors();
        }

        @Test
        void should_set_context_attribute() {
            var policy = new GroovyPolicy(buildConfig("set_context_attribute.groovy"));
            policy.onRequest(kafkaExecutionContext).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            verify(kafkaExecutionContext, times(1)).setAttribute("count", 100);
        }

        @Test
        void should_fail_with_result_failure() {
            var policy = new GroovyPolicy(buildConfig("break_request.groovy"));

            policy.onRequest(kafkaExecutionContext).test().awaitDone(10, TimeUnit.SECONDS).assertError(Throwable.class);
        }

        @Test
        void should_fail_with_invalid_script() {
            var policy = new GroovyPolicy(buildConfig("invalid_script.groovy"));

            policy.onRequest(kafkaExecutionContext).test().awaitDone(10, TimeUnit.SECONDS).assertError(Throwable.class);
        }
    }

    @Nested
    class OnKafkaResponse {

        @Test
        void should_complete_when_no_script() {
            var policy = new GroovyPolicy(buildConfig(null));
            policy.onResponse(kafkaExecutionContext).test().assertComplete().assertNoErrors();
        }

        @Test
        void should_set_context_attribute() {
            var policy = new GroovyPolicy(buildConfig("set_context_attribute.groovy"));
            policy.onResponse(kafkaExecutionContext).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            verify(kafkaExecutionContext, times(1)).setAttribute("count", 100);
        }

        @Test
        void should_fail_with_result_failure() {
            var policy = new GroovyPolicy(buildConfig("break_request.groovy"));

            policy.onResponse(kafkaExecutionContext).test().awaitDone(10, TimeUnit.SECONDS).assertError(Throwable.class);
        }
    }

    @Nested
    class OnKafkaMessageRequest {

        @Test
        void should_complete_when_no_script() {
            var policy = new GroovyPolicy(buildConfig(null));
            policy.onMessageRequest(kafkaMessageExecutionContext).test().assertComplete().assertNoErrors();
        }

        @Test
        void should_set_kafka_message_attributes() {
            var policy = new GroovyPolicy(buildConfig("set_kafka_message_attribute.groovy"));

            when(kafkaMessageRequest.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageRequest(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("my-topic", "my-key", Buffer.buffer("payload"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            assertThat(message.attributes()).containsEntry("topic", "my-topic");
            assertThat(message.attributes()).containsEntry("keyValue", "my-key"); // message.key now returns String
        }

        @Test
        void should_set_context_attribute_on_kafka_message_request() {
            var policy = new GroovyPolicy(buildConfig("set_context_attribute.groovy"));

            when(kafkaMessageRequest.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageRequest(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("topic", "key", Buffer.buffer("payload"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            verify(kafkaExecutionContext, times(1)).setAttribute("count", 100);
        }

        @Test
        void should_override_kafka_message_content() {
            var config = GroovyPolicyConfiguration.builder().script("'new content'").readContent(false).overrideContent(true).build();

            var policy = new GroovyPolicy(config);

            when(kafkaMessageRequest.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageRequest(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("topic", "key", Buffer.buffer("original"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            var contentCaptor = ArgumentCaptor.forClass(Buffer.class);
            verify(message).content(contentCaptor.capture());
            assertThat(contentCaptor.getValue().toString()).isEqualTo("new content");
        }

        @Test
        void should_fail_with_result_failure() {
            var policy = new GroovyPolicy(buildConfig("break_request.groovy"));

            when(kafkaMessageRequest.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageRequest(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("topic", "key", Buffer.buffer("payload"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertError(Throwable.class);
        }

        @Test
        void should_set_kafka_record_header_with_subscript_string_assignment() {
            var policy = new GroovyPolicy(buildConfig("set_kafka_message_header_subscript.groovy"));

            when(kafkaMessageRequest.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageRequest(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("topic", "key", Buffer.buffer("payload"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            assertThat(message.recordHeaders()).containsKey("X-Internal-Status");
            assertThat(message.recordHeaders().get("X-Internal-Status").toString()).isEqualTo("Processed");
        }

        @Test
        void should_set_kafka_record_header_with_subscript_list_assignment() {
            var policy = new GroovyPolicy(buildConfig("set_kafka_message_header_subscript_list.groovy"));

            when(kafkaMessageRequest.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageRequest(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("topic", "key", Buffer.buffer("payload"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            // Kafka record headers are single-valued; the Groovy binding keeps the last element.
            assertThat(message.recordHeaders()).containsKey("X-Trace");
            assertThat(message.recordHeaders().get("X-Trace").toString()).isEqualTo("second");
        }

        @Test
        void should_read_kafka_record_header_as_list() {
            var policy = new GroovyPolicy(buildConfig("read_kafka_message_header.groovy"));

            when(kafkaMessageRequest.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageRequest(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("topic", "key", Buffer.buffer("payload"));
            message.putRecordHeader("X-Incoming", Buffer.buffer("value-from-broker"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            assertThat(message.attributes()).containsEntry("echoedHeader", "value-from-broker");
        }

        @Test
        void should_remove_kafka_record_header() {
            var policy = new GroovyPolicy(buildConfig("remove_kafka_message_header.groovy"));

            when(kafkaMessageRequest.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageRequest(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("topic", "key", Buffer.buffer("payload"));
            message.putRecordHeader("X-Remove-Me", Buffer.buffer("doomed"));
            message.putRecordHeader("X-Keep", Buffer.buffer("kept"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            assertThat(message.recordHeaders()).doesNotContainKey("X-Remove-Me");
            assertThat(message.recordHeaders()).containsKey("X-Keep");
        }
    }

    @Nested
    class OnKafkaMessageResponse {

        @Test
        void should_complete_when_no_script() {
            var policy = new GroovyPolicy(buildConfig(null));
            policy.onMessageResponse(kafkaMessageExecutionContext).test().assertComplete().assertNoErrors();
        }

        @Test
        void should_set_kafka_message_attributes() {
            var policy = new GroovyPolicy(buildConfig("set_kafka_message_attribute.groovy"));

            when(kafkaMessageResponse.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageResponse(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("my-topic", "my-key", Buffer.buffer("payload"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            assertThat(message.attributes()).containsEntry("topic", "my-topic");
            assertThat(message.attributes()).containsEntry("keyValue", "my-key"); // message.key now returns String
        }

        @Test
        void should_set_context_attribute_on_kafka_message_response() {
            var policy = new GroovyPolicy(buildConfig("set_context_attribute.groovy"));

            when(kafkaMessageResponse.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageResponse(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("topic", "key", Buffer.buffer("payload"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            verify(kafkaExecutionContext, times(1)).setAttribute("count", 100);
        }

        @Test
        void should_override_kafka_message_content() {
            var config = GroovyPolicyConfiguration.builder().script("'new content'").readContent(false).overrideContent(true).build();

            var policy = new GroovyPolicy(config);

            when(kafkaMessageResponse.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageResponse(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("topic", "key", Buffer.buffer("original"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertComplete().assertNoErrors();

            var contentCaptor = ArgumentCaptor.forClass(Buffer.class);
            verify(message).content(contentCaptor.capture());
            assertThat(contentCaptor.getValue().toString()).isEqualTo("new content");
        }

        @Test
        void should_fail_with_result_failure() {
            var policy = new GroovyPolicy(buildConfig("break_request.groovy"));

            when(kafkaMessageResponse.onMessage(onMessageCaptor.capture())).thenReturn(Completable.complete());
            policy.onMessageResponse(kafkaMessageExecutionContext).test().assertNoValues();

            var message = mockKafkaMessage("topic", "key", Buffer.buffer("payload"));

            onMessageCaptor.getValue().apply(message).test().awaitDone(10, TimeUnit.SECONDS).assertError(Throwable.class);
        }
    }

    private static KafkaMessage mockKafkaMessage(String topic, String key, Buffer content) {
        var message = mock(KafkaMessage.class, withSettings().lenient());
        Map<String, Object> attributes = new HashMap<>();
        Map<String, Buffer> recordHeaders = new LinkedHashMap<>();

        when(message.topic()).thenReturn(topic);
        when(message.key()).thenReturn(Buffer.buffer(key));
        when(message.content()).thenReturn(content);
        when(message.attributes()).thenReturn(attributes);
        when(message.attribute(any(String.class), any())).thenAnswer(invocation -> {
            attributes.put(invocation.getArgument(0), invocation.getArgument(1));
            return message;
        });
        when(message.recordHeaders()).thenAnswer(invocation -> Collections.unmodifiableMap(recordHeaders));
        when(message.putRecordHeader(any(String.class), any(Buffer.class))).thenAnswer(invocation -> {
            recordHeaders.put(invocation.getArgument(0), invocation.getArgument(1));
            return message;
        });
        when(message.removeRecordHeader(any(String.class))).thenAnswer(invocation -> {
            recordHeaders.remove((String) invocation.getArgument(0));
            return message;
        });

        return message;
    }

    private static GroovyPolicyConfiguration buildConfig(String script) {
        var builder = GroovyPolicyConfiguration.builder().readContent(false);
        if (script != null) {
            builder.script(loadScript(script));
        }
        return builder.build();
    }

    private static String loadScript(String file) {
        try {
            return new String(getResourceAsStream(file).readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read script bytes from file " + file, e);
        }
    }

    private static InputStream getResourceAsStream(String file) {
        var resourceAsStream = GroovyPolicy.class.getResourceAsStream(file);

        if (resourceAsStream == null) {
            throw new IllegalArgumentException("Unable to find script file " + file);
        }

        return resourceAsStream;
    }
}
