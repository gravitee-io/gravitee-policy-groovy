/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.groovy;

import groovy.lang.Binding;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.stream.TransformableRequestStreamBuilder;
import io.gravitee.gateway.api.http.stream.TransformableResponseStreamBuilder;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.exception.TransformationException;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.api.annotations.OnResponse;
import io.gravitee.policy.api.annotations.OnResponseContent;
import io.gravitee.policy.groovy.configuration.GroovyPolicyConfiguration;
import io.gravitee.policy.groovy.model.ContentAwareRequest;
import io.gravitee.policy.groovy.model.ContentAwareResponse;
import io.gravitee.policy.groovy.sandbox.SecuredGroovyShell;
import io.gravitee.policy.groovy.utils.AttributesBasedExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroovyPolicy {

    private static final Logger logger = LoggerFactory.getLogger(GroovyPolicy.class);

    private final GroovyPolicyConfiguration groovyPolicyConfiguration;

    private static final String REQUEST_VARIABLE_NAME = "request";
    private static final String RESPONSE_VARIABLE_NAME = "response";
    private static final String CONTEXT_VARIABLE_NAME = "context";
    private static final String RESULT_VARIABLE_NAME = "result";

    private static final SecuredGroovyShell GROOVY_SHELL = new SecuredGroovyShell();

    public GroovyPolicy(GroovyPolicyConfiguration groovyPolicyConfiguration) {
        this.groovyPolicyConfiguration = groovyPolicyConfiguration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext executionContext, PolicyChain policyChain) {
        executeScript(request, response, executionContext, policyChain, groovyPolicyConfiguration.getOnRequestScript());
    }

    @OnResponse
    public void onResponse(Request request, Response response, ExecutionContext executionContext, PolicyChain policyChain) {
        executeScript(request, response, executionContext, policyChain, groovyPolicyConfiguration.getOnResponseScript());
    }

    @OnResponseContent
    public ReadWriteStream onResponseContent(
        Request request,
        Response response,
        ExecutionContext executionContext,
        PolicyChain policyChain
    ) {
        String script = groovyPolicyConfiguration.getOnResponseContentScript();

        if (script != null && !script.trim().isEmpty()) {
            return TransformableResponseStreamBuilder
                .on(response)
                .chain(policyChain)
                .transform(
                    buffer -> {
                        try {
                            final String content = executeStreamScript(
                                new ContentAwareRequest(request, null),
                                new ContentAwareResponse(response, buffer.toString()),
                                executionContext,
                                script
                            );
                            return Buffer.buffer(content);
                        } catch (PolicyFailureException ex) {
                            if (ex.getResult().getContentType() != null) {
                                policyChain.streamFailWith(
                                    io.gravitee.policy.api.PolicyResult.failure(
                                        ex.getResult().getKey(),
                                        ex.getResult().getCode(),
                                        ex.getResult().getError(),
                                        ex.getResult().getContentType()
                                    )
                                );
                            } else {
                                policyChain.streamFailWith(
                                    io.gravitee.policy.api.PolicyResult.failure(
                                        ex.getResult().getKey(),
                                        ex.getResult().getCode(),
                                        ex.getResult().getError()
                                    )
                                );
                            }
                        } catch (Throwable t) {
                            logger.error("Unable to run Groovy script", t);
                            throw new TransformationException("Unable to run Groovy script: " + t.getMessage(), t);
                        }
                        return null;
                    }
                )
                .build();
        }

        return null;
    }

    @OnRequestContent
    public ReadWriteStream onRequestContent(
        Request request,
        Response response,
        ExecutionContext executionContext,
        PolicyChain policyChain
    ) {
        String script = groovyPolicyConfiguration.getOnRequestContentScript();

        if (script != null && !script.trim().isEmpty()) {
            return TransformableRequestStreamBuilder
                .on(request)
                .chain(policyChain)
                .transform(
                    buffer -> {
                        try {
                            final String content = executeStreamScript(
                                new ContentAwareRequest(request, buffer.toString()),
                                new ContentAwareResponse(response, null),
                                executionContext,
                                script
                            );

                            return Buffer.buffer(content);
                        } catch (PolicyFailureException ex) {
                            if (ex.getResult().getContentType() != null) {
                                policyChain.streamFailWith(
                                    io.gravitee.policy.api.PolicyResult.failure(
                                        ex.getResult().getKey(),
                                        ex.getResult().getCode(),
                                        ex.getResult().getError(),
                                        ex.getResult().getContentType()
                                    )
                                );
                            } else {
                                policyChain.streamFailWith(
                                    io.gravitee.policy.api.PolicyResult.failure(
                                        ex.getResult().getKey(),
                                        ex.getResult().getCode(),
                                        ex.getResult().getError()
                                    )
                                );
                            }
                        } catch (Throwable t) {
                            logger.error("Unable to run Groovy script", t);
                            throw new TransformationException("Unable to run Groovy script: " + t.getMessage(), t);
                        }
                        return null;
                    }
                )
                .build();
        }

        return null;
    }

    private String executeScript(
        Request request,
        Response response,
        ExecutionContext executionContext,
        PolicyChain policyChain,
        String script
    ) {
        if (script == null || script.trim().isEmpty()) {
            policyChain.doNext(request, response);
        } else {
            try {
                // Prepare binding
                Binding binding = new Binding();
                binding.setVariable(REQUEST_VARIABLE_NAME, new ContentAwareRequest(request, null));
                binding.setVariable(RESPONSE_VARIABLE_NAME, new ContentAwareResponse(response, null));
                binding.setVariable(CONTEXT_VARIABLE_NAME, new AttributesBasedExecutionContext(executionContext));
                binding.setVariable(RESULT_VARIABLE_NAME, new PolicyResult());

                // And run script
                GROOVY_SHELL.evaluate(script, binding);

                PolicyResult result = (PolicyResult) binding.getVariable(RESULT_VARIABLE_NAME);

                if (result.getState() == PolicyResult.State.SUCCESS) {
                    policyChain.doNext(request, response);
                } else {
                    if (result.getContentType() != null) {
                        policyChain.failWith(
                            io.gravitee.policy.api.PolicyResult.failure(
                                result.getKey(),
                                result.getCode(),
                                result.getError(),
                                result.getContentType()
                            )
                        );
                    } else {
                        policyChain.failWith(
                            io.gravitee.policy.api.PolicyResult.failure(result.getKey(), result.getCode(), result.getError())
                        );
                    }
                }
            } catch (Throwable t) {
                logger.error("Unable to run Groovy script", t);
                policyChain.failWith(io.gravitee.policy.api.PolicyResult.failure(t.getMessage()));
            }
        }

        return null;
    }

    private String executeStreamScript(Request request, Response response, ExecutionContext executionContext, String script)
        throws PolicyFailureException {
        // Prepare binding
        Binding binding = new Binding();
        binding.setVariable(REQUEST_VARIABLE_NAME, request);
        binding.setVariable(RESPONSE_VARIABLE_NAME, response);
        binding.setVariable(CONTEXT_VARIABLE_NAME, new AttributesBasedExecutionContext(executionContext));
        binding.setVariable(RESULT_VARIABLE_NAME, new PolicyResult());

        // And run script
        String content = GROOVY_SHELL.evaluate(script, binding);

        PolicyResult result = (PolicyResult) binding.getVariable(RESULT_VARIABLE_NAME);
        if (result.getState() == PolicyResult.State.FAILURE) {
            throw new PolicyFailureException(result);
        }

        return content;
    }

    private static class PolicyFailureException extends Exception {

        private final PolicyResult result;

        PolicyFailureException(PolicyResult result) {
            this.result = result;
        }

        public PolicyResult getResult() {
            return result;
        }
    }
}
