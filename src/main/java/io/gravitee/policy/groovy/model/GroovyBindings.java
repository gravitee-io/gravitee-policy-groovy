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
import io.gravitee.policy.groovy.model.http.ScriptableHttpRequest;
import io.gravitee.policy.groovy.model.http.ScriptableHttpResponse;
import io.gravitee.policy.groovy.model.message.ScriptableMessage;

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

    public static Binding bindRequest(HttpExecutionContext ctx, Buffer bodyBuffer) {
        var binding = bindCommon(ctx);
        binding.setVariable(REQUEST_VARIABLE_NAME, new ScriptableHttpRequest(ctx.request(), bodyBuffer));
        binding.setVariable(RESPONSE_VARIABLE_NAME, new ScriptableHttpResponse(ctx.response()));
        return binding;
    }

    public static Binding bindResponse(HttpExecutionContext ctx, Buffer bodyBuffer) {
        var binding = bindCommon(ctx);
        binding.setVariable(REQUEST_VARIABLE_NAME, new ScriptableHttpRequest(ctx.request()));
        binding.setVariable(RESPONSE_VARIABLE_NAME, new ScriptableHttpResponse(ctx.response(), bodyBuffer));
        return binding;
    }

    public static Binding bindMessage(MessageExecutionContext ctx, Message message) {
        var binding = bindCommon(ctx);
        binding.setVariable(MESSAGE_VARIABLE, new ScriptableMessage(message));
        return binding;
    }

    private static Binding bindCommon(GenericExecutionContext ctx) {
        var binding = new Binding();
        binding.setVariable(CONTEXT_VARIABLE_NAME, new ScriptableExecutionContext(ctx));
        binding.setVariable(RESULT_VARIABLE_NAME, new PolicyResult());
        return binding;
    }
}
