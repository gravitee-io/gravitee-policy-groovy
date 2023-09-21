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

import static io.gravitee.common.http.HttpStatusCode.*;

import groovy.lang.Binding;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.policy.groovy.PolicyResult.State;
import io.gravitee.policy.groovy.configuration.GroovyPolicyConfiguration;
import io.gravitee.policy.groovy.model.GroovyBindings;
import io.gravitee.policy.v3.groovy.GroovyPolicyV3;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class GroovyPolicy extends GroovyPolicyV3 implements Policy {

    private static final ExecutionFailure DEFAULT_EXECUTION_FAILURE = new ExecutionFailure(INTERNAL_SERVER_ERROR_500)
        .key("GROOVY_EXECUTION_FAILURE")
        .message("Groovy execution failure");

    private final Flowable<String> scriptFlowable;

    public GroovyPolicy(GroovyPolicyConfiguration configuration) {
        super(configuration);
        scriptFlowable = Flowable.fromIterable(configuration.getScripts());
    }

    @Override
    public String id() {
        return "policy-groovy";
    }

    @Override
    public Completable onRequest(HttpExecutionContext ctx) {
        return ctx
            .request()
            .onBody(bodyBuffer ->
                bodyBuffer
                    .defaultIfEmpty(Buffer.buffer())
                    .flatMapMaybe(buffer -> onHttp(ctx, buffer, ctx.request().headers(), GroovyBindings.bindRequest(ctx, buffer)))
            );
    }

    @Override
    public Completable onResponse(HttpExecutionContext ctx) {
        return ctx
            .response()
            .onBody(bodyBuffer ->
                bodyBuffer
                    .defaultIfEmpty(Buffer.buffer())
                    .flatMapMaybe(buffer -> onHttp(ctx, buffer, ctx.response().headers(), GroovyBindings.bindResponse(ctx, buffer)))
            );
    }

    private Maybe<Buffer> onHttp(HttpExecutionContext ctx, Buffer bodyBuffer, HttpHeaders headers, Binding binding) {
        return scriptFlowable
            .flatMapMaybe(script -> runScript(ctx, binding, script))
            .lastElement()
            .filter(groovyBuffer -> configuration.isOverrideContent())
            .doOnSuccess(groovyBuffer -> setContentLength(headers, groovyBuffer))
            .switchIfEmpty(Maybe.just(bodyBuffer));
    }

    private Maybe<Buffer> runScript(HttpExecutionContext ctx, Binding binding, String script) {
        try {
            var content = GROOVY_SHELL.evaluate(script, binding);
            var result = (PolicyResult) binding.getVariable(GroovyBindings.RESULT_VARIABLE_NAME);
            return handleResult(ctx, result, content);
        } catch (Exception e) {
            return ctx.interruptBodyWith(DEFAULT_EXECUTION_FAILURE);
        }
    }

    private Maybe<Buffer> handleResult(HttpExecutionContext ctx, PolicyResult result, Object content) {
        if (result.getState() == State.FAILURE) {
            return ctx.interruptBodyWith(
                new ExecutionFailure(result.getCode()).key(result.getKey()).message(result.getError()).contentType(result.getContentType())
            );
        }

        return content == null ? Maybe.just(Buffer.buffer()) : Maybe.just(Buffer.buffer(content.toString()));
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
        try {
            var script = configuration.getScript();
            var binding = GroovyBindings.bindMessage(ctx, message);
            var content = GROOVY_SHELL.evaluate(script, binding);
            var result = (PolicyResult) binding.getVariable(GroovyBindings.RESULT_VARIABLE_NAME);
            return handleResult(ctx, message, result, content);
        } catch (Exception e) {
            return ctx.interruptMessageWith(DEFAULT_EXECUTION_FAILURE);
        }
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
}
