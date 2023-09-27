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

import groovy.lang.Binding;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.context.GenericExecutionContext;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.policy.groovy.PolicyResult;
import io.gravitee.policy.groovy.model.http.BindableHttpRequest;
import io.gravitee.policy.groovy.model.http.BindableHttpResponse;
import io.gravitee.policy.groovy.model.message.BindableMessage;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroovyBindings {

    public static final String REQUEST_VARIABLE_NAME = "request";
    public static final String RESPONSE_VARIABLE_NAME = "response";
    public static final String MESSAGE_VARIABLE = "message";
    public static final String CONTEXT_VARIABLE_NAME = "context";
    public static final String RESULT_VARIABLE_NAME = "result";

    private GroovyBindings() {}

    public static Binding bindHttp(HttpExecutionContext ctx) {
        var binding = bindCommon(ctx);
        binding.setVariable(REQUEST_VARIABLE_NAME, new BindableHttpRequest(ctx.request()));
        binding.setVariable(RESPONSE_VARIABLE_NAME, new BindableHttpResponse(ctx.response()));
        return binding;
    }

    public static Binding bindRequestContent(HttpExecutionContext ctx, Buffer bodyBuffer) {
        var binding = bindCommon(ctx);
        binding.setVariable(REQUEST_VARIABLE_NAME, new BindableHttpRequest(ctx.request(), bodyBuffer));
        binding.setVariable(RESPONSE_VARIABLE_NAME, new BindableHttpResponse(ctx.response()));
        return binding;
    }

    public static Binding bindResponseContent(HttpExecutionContext ctx, Buffer bodyBuffer) {
        var binding = bindCommon(ctx);
        binding.setVariable(REQUEST_VARIABLE_NAME, new BindableHttpRequest(ctx.request()));
        binding.setVariable(RESPONSE_VARIABLE_NAME, new BindableHttpResponse(ctx.response(), bodyBuffer));
        return binding;
    }

    public static Binding bindMessage(MessageExecutionContext ctx, Message message) {
        var binding = bindCommon(ctx);
        binding.setVariable(MESSAGE_VARIABLE, new BindableMessage(message));
        return binding;
    }

    private static Binding bindCommon(GenericExecutionContext ctx) {
        var binding = new Binding();
        binding.setVariable(CONTEXT_VARIABLE_NAME, new BindableExecutionContext(ctx));
        binding.setVariable(RESULT_VARIABLE_NAME, new PolicyResult());
        return binding;
    }
}
