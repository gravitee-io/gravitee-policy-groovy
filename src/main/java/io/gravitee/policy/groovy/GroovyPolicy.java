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

import static io.gravitee.common.http.HttpStatusCode.INTERNAL_SERVER_ERROR_500;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import groovy.lang.Binding;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.gateway.reactive.api.policy.kafka.KafkaPolicy;
import io.gravitee.policy.groovy.PolicyResult.State;
import io.gravitee.policy.groovy.configuration.GroovyPolicyConfiguration;
import io.gravitee.policy.groovy.model.GroovyBindings;
import io.gravitee.policy.v3.groovy.GroovyPolicyV3;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.protocol.Errors;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class GroovyPolicy extends GroovyPolicyV3 implements Policy, KafkaPolicy {

    public static final String SCRIPT_EXECUTION_ERROR_MESSAGE = "An error occurred while executing Groovy script";
    /**
     * @see GroovyPolicyConfiguration#getScripts()
     */
    private final Flowable<String> scriptFlowable;

    public GroovyPolicy(GroovyPolicyConfiguration configuration) {
        super(configuration);
        scriptFlowable = Flowable.fromIterable(configuration.getScripts());

        // Precompile all the scripts on a io schedulers to get ready when necessary.
        scriptFlowable
            .doOnNext(GROOVY_SHELL::compile)
            .subscribeOn(Schedulers.io())
            .doOnError(e -> log.warn("Error while compiling script. Ignoring", e))
            .onErrorComplete()
            .subscribe();
    }

    @Override
    public String id() {
        return "policy-groovy";
    }

    @Override
    public Completable onRequest(HttpExecutionContext ctx) {
        if (
            isBlank(configuration.getScript()) &&
            isBlank(configuration.getOnRequestScript()) &&
            isBlank(configuration.getOnRequestContentScript())
        ) {
            return Completable.complete();
        }

        if (
            (isNotBlank(configuration.getScript()) && configuration.isReadContent()) ||
            isNotBlank(configuration.getOnRequestContentScript())
        ) {
            return onRequestContent(ctx);
        }
        if (isNotBlank(configuration.getScript())) {
            return runScript(ctx, GroovyBindings.bindHttp(ctx), configuration.getScript());
        }
        return runScript(ctx, GroovyBindings.bindHttp(ctx), configuration.getOnRequestScript());
    }

    private Completable onRequestContent(HttpExecutionContext ctx) {
        return ctx
            .request()
            .onBody(bodyBuffer ->
                bodyBuffer
                    .defaultIfEmpty(Buffer.buffer())
                    .flatMapMaybe(buffer ->
                        onHttpContent(ctx, buffer, ctx.request().headers(), GroovyBindings.bindRequestContent(ctx, buffer))
                    )
            );
    }

    @Override
    public Completable onResponse(HttpExecutionContext ctx) {
        if (
            isBlank(configuration.getScript()) &&
            isBlank(configuration.getOnResponseScript()) &&
            isBlank(configuration.getOnResponseContentScript())
        ) {
            return Completable.complete();
        }

        if (
            (isNotBlank(configuration.getScript()) && configuration.isReadContent()) ||
            isNotBlank(configuration.getOnResponseContentScript())
        ) {
            return onResponseContent(ctx);
        }
        if (isNotBlank(configuration.getScript())) {
            return runScript(ctx, GroovyBindings.bindHttp(ctx), configuration.getScript());
        }
        return runScript(ctx, GroovyBindings.bindHttp(ctx), configuration.getOnResponseScript());
    }

    private Completable onResponseContent(HttpExecutionContext ctx) {
        return ctx
            .response()
            .onBody(bodyBuffer ->
                bodyBuffer
                    .defaultIfEmpty(Buffer.buffer())
                    .flatMapMaybe(buffer ->
                        onHttpContent(ctx, buffer, ctx.response().headers(), GroovyBindings.bindResponseContent(ctx, buffer))
                    )
            );
    }

    private Maybe<Buffer> onHttpContent(HttpExecutionContext ctx, Buffer bodyBuffer, HttpHeaders headers, Binding binding) {
        return scriptFlowable
            .concatMapMaybe(script -> runContentAwareScript(ctx, binding, script))
            .lastElement()
            .filter(groovyBuffer -> configuration.isOverrideContent())
            .doOnSuccess(groovyBuffer -> setContentLength(headers, groovyBuffer))
            .switchIfEmpty(Maybe.just(bodyBuffer));
    }

    private Maybe<Buffer> runContentAwareScript(HttpExecutionContext ctx, Binding binding, String script) {
        return GROOVY_SHELL.evaluateRx(script, binding)
            .onErrorResumeNext(e -> {
                log.error(SCRIPT_EXECUTION_ERROR_MESSAGE, e);
                return ctx.interruptBodyWith(
                    new ExecutionFailure(INTERNAL_SERVER_ERROR_500)
                        .key("GROOVY_EXECUTION_FAILURE")
                        .message("Internal Server Error")
                        .cause(e)
                );
            })
            .flatMap(content -> {
                var result = (PolicyResult) binding.getVariable(GroovyBindings.RESULT_VARIABLE_NAME);
                return handleResult(ctx, result, content);
            });
    }

    private Maybe<Buffer> handleResult(HttpExecutionContext ctx, PolicyResult result, Object content) {
        if (result.getState() == State.FAILURE) {
            return ctx.interruptBodyWith(
                new ExecutionFailure(result.getCode()).key(result.getKey()).message(result.getError()).contentType(result.getContentType())
            );
        }

        return content == null ? Maybe.just(Buffer.buffer()) : Maybe.just(Buffer.buffer(content.toString()));
    }

    private Completable runScript(HttpExecutionContext ctx, Binding binding, String script) {
        return GROOVY_SHELL.evaluateRx(script, binding)
            .ignoreElement()
            .onErrorResumeNext(e -> {
                log.error(SCRIPT_EXECUTION_ERROR_MESSAGE, e);
                return ctx.interruptWith(
                    new ExecutionFailure(INTERNAL_SERVER_ERROR_500)
                        .key("GROOVY_EXECUTION_FAILURE")
                        .message("Internal Server Error")
                        .cause(e)
                );
            })
            .andThen(
                Completable.defer(() -> {
                    var result = (PolicyResult) binding.getVariable(GroovyBindings.RESULT_VARIABLE_NAME);
                    return handleResult(ctx, result);
                })
            );
    }

    private Completable handleResult(HttpExecutionContext ctx, PolicyResult result) {
        if (result.getState() == State.FAILURE) {
            return ctx.interruptWith(
                new ExecutionFailure(result.getCode()).key(result.getKey()).message(result.getError()).contentType(result.getContentType())
            );
        }

        return Completable.complete();
    }

    private static void setContentLength(final HttpHeaders headers, final Buffer buffer) {
        headers.set(HttpHeaderNames.CONTENT_LENGTH, Integer.toString(buffer.length()));
    }

    @Override
    public Completable onMessageRequest(MessageExecutionContext ctx) {
        return ctx.request().onMessage(message -> runScript(ctx, message));
    }

    @Override
    public Completable onMessageResponse(MessageExecutionContext ctx) {
        return ctx.response().onMessage(message -> runScript(ctx, message));
    }

    private Maybe<Message> runScript(MessageExecutionContext ctx, Message message) {
        var script = configuration.getScript();
        var binding = GroovyBindings.bindMessage(ctx, message);

        return GROOVY_SHELL.evaluateRx(script, binding)
            .onErrorResumeNext(e ->
                ctx.interruptMessageWith(
                    new ExecutionFailure(INTERNAL_SERVER_ERROR_500)
                        .key("GROOVY_EXECUTION_FAILURE")
                        .message("Internal Server Error")
                        .cause(e)
                )
            )
            .flatMap(content -> {
                var result = (PolicyResult) binding.getVariable(GroovyBindings.RESULT_VARIABLE_NAME);
                return handleResult(ctx, message, result, content);
            });
    }

    private Maybe<Message> handleResult(MessageExecutionContext ctx, Message message, PolicyResult result, Object content) {
        if (result.getState() == State.FAILURE) {
            return ctx.interruptMessageWith(
                new ExecutionFailure(result.getCode()).key(result.getKey()).message(result.getError()).contentType(result.getContentType())
            );
        }

        if (configuration.isOverrideContent()) {
            message.content(content == null ? Buffer.buffer() : Buffer.buffer(content.toString()));
        }

        return Maybe.just(message);
    }

    @Override
    public Completable onRequest(KafkaExecutionContext ctx) {
        return executeKafkaScript(ctx, configuration.getOnRequestScript());
    }

    @Override
    public Completable onResponse(KafkaExecutionContext ctx) {
        return executeKafkaScript(ctx, configuration.getOnResponseScript());
    }

    @Override
    public Completable onMessageRequest(KafkaMessageExecutionContext ctx) {
        return onKafkaMessage(ctx, ctx.request()::onMessage);
    }

    @Override
    public Completable onMessageResponse(KafkaMessageExecutionContext ctx) {
        return onKafkaMessage(ctx, ctx.response()::onMessage);
    }

    private Completable executeKafkaScript(KafkaExecutionContext ctx, String phaseScript) {
        final String script = isNotBlank(phaseScript) ? phaseScript : configuration.getScript();
        if (isBlank(script)) {
            return Completable.complete();
        }
        return runKafkaScript(ctx, GroovyBindings.bindKafka(ctx), script);
    }

    private Completable onKafkaMessage(
        KafkaMessageExecutionContext ctx,
        java.util.function.Function<java.util.function.Function<KafkaMessage, Maybe<KafkaMessage>>, Completable> onMessage
    ) {
        if (isBlank(configuration.getScript())) {
            return Completable.complete();
        }
        return onMessage.apply(message -> runKafkaMessageScript(ctx, message));
    }

    private Completable runKafkaScript(KafkaExecutionContext ctx, Binding binding, String script) {
        return GROOVY_SHELL.evaluateRx(script, binding)
            .ignoreElement()
            .onErrorResumeNext(e -> {
                log.error(SCRIPT_EXECUTION_ERROR_MESSAGE, e);
                return ctx.interruptWith(Errors.UNKNOWN_SERVER_ERROR);
            })
            .andThen(
                Completable.defer(() -> {
                    var result = (PolicyResult) binding.getVariable(GroovyBindings.RESULT_VARIABLE_NAME);
                    if (result.getState() == State.FAILURE) {
                        return ctx.interruptWith(Errors.UNKNOWN_SERVER_ERROR);
                    }
                    return Completable.complete();
                })
            );
    }

    private Maybe<KafkaMessage> runKafkaMessageScript(KafkaMessageExecutionContext ctx, KafkaMessage message) {
        var script = configuration.getScript();
        var binding = GroovyBindings.bindKafkaMessage(ctx, message);

        return GROOVY_SHELL.evaluateRx(script, binding)
            .onErrorResumeNext(e -> {
                log.error("An error occurred while executing Groovy script on Kafka message", e);
                return ctx.executionContext().interruptWith(Errors.UNKNOWN_SERVER_ERROR).toMaybe();
            })
            .flatMap(content -> {
                if (configuration.isOverrideContent()) {
                    message.content(content == null ? Buffer.buffer() : Buffer.buffer(content.toString()));
                }
                return Maybe.just(message);
            })
            // Scripts that return null emit an empty Maybe, bypassing flatMap above.
            // switchIfEmpty ensures the message still propagates when the script has no return value.
            .switchIfEmpty(Maybe.defer(() -> Maybe.just(message)))
            .flatMap(m -> {
                var result = (PolicyResult) binding.getVariable(GroovyBindings.RESULT_VARIABLE_NAME);
                if (result.getState() == State.FAILURE) {
                    return ctx.executionContext().interruptWith(Errors.UNKNOWN_SERVER_ERROR).toMaybe();
                }
                return Maybe.just(m);
            });
    }
}
