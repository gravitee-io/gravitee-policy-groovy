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
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
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
import io.gravitee.policy.groovy.utils.Sha1;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroovyPolicy {

    private final GroovyPolicyConfiguration groovyPolicyConfiguration;

    private final static String REQUEST_VARIABLE_NAME = "request";
    private final static String RESPONSE_VARIABLE_NAME = "response";
    private final static String CONTEXT_VARIABLE_NAME = "context";
    private final static String RESULT_VARIABLE_NAME = "result";

    private static final GroovyShell GROOVY_SHELL = new GroovyShell();

    private static final ConcurrentMap<String, Class<?>> sources = new ConcurrentHashMap<>();

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
    public ReadWriteStream onResponseContent(Request request, Response response, ExecutionContext executionContext,
                                             PolicyChain policyChain) {
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
                                            script);
                                    return Buffer.buffer(content);
                                } catch (PolicyFailureException ex) {
                                    if (ex.getResult().getContentType() != null) {
                                        policyChain.streamFailWith(io.gravitee.policy.api.PolicyResult.failure(
                                                ex.getResult().getCode(), ex.getResult().getError(),ex.getResult().getContentType()));
                                    } else {
                                        policyChain.streamFailWith(io.gravitee.policy.api.PolicyResult.failure(
                                                ex.getResult().getCode(), ex.getResult().getError()));
                                    }
                                } catch (Throwable t) {
                                    throw new TransformationException("Unable to run Groovy script: " + t.getMessage(), t);
                                }
                                return null;
                            }
                    ).build();
        }

        return null;
    }

    @OnRequestContent
    public ReadWriteStream onRequestContent(Request request, Response response, ExecutionContext executionContext,
                                            PolicyChain policyChain) {
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
                                            script);

                                    return Buffer.buffer(content);
                                } catch (PolicyFailureException ex) {
                                    if (ex.getResult().getContentType() != null) {
                                        policyChain.streamFailWith(io.gravitee.policy.api.PolicyResult.failure(
                                                ex.getResult().getCode(), ex.getResult().getError(),ex.getResult().getContentType()));
                                    } else {
                                        policyChain.streamFailWith(io.gravitee.policy.api.PolicyResult.failure(
                                                ex.getResult().getCode(), ex.getResult().getError()));
                                    }
                                } catch (Throwable t) {
                                    throw new TransformationException("Unable to run Groovy script: " + t.getMessage(), t);
                                }
                                return null;
                            }
                    ).build();
        }

        return null;
    }

    private String executeScript(Request request, Response response, ExecutionContext executionContext,
                                 PolicyChain policyChain, String script) {
        if (script == null || script.trim().isEmpty()) {
            policyChain.doNext(request, response);
        } else {
            try {
                // Get script class
                Class<?> scriptClass = getOrCreate(script);

                // Prepare binding
                Binding binding = new Binding();
                binding.setVariable(REQUEST_VARIABLE_NAME, new ContentAwareRequest(request, null));
                binding.setVariable(RESPONSE_VARIABLE_NAME, new ContentAwareResponse(response, null));
                binding.setVariable(CONTEXT_VARIABLE_NAME, executionContext);
                binding.setVariable(RESULT_VARIABLE_NAME, new PolicyResult());

                // And run script
                Script gScript = InvokerHelper.createScript(scriptClass, binding);
                gScript.run();

                PolicyResult result = (PolicyResult) binding.getVariable(RESULT_VARIABLE_NAME);

                if (result.getState() == PolicyResult.State.SUCCESS) {
                    policyChain.doNext(request, response);
                } else {
                    if (result.getContentType() != null) {
                        policyChain.failWith(io.gravitee.policy.api.PolicyResult.failure(
                                result.getCode(),
                                result.getError(),
                                result.getContentType()));
                    } else {
                        policyChain.failWith(io.gravitee.policy.api.PolicyResult.failure(
                                result.getCode(),
                                result.getError()));
                    }
                }
            } catch (Throwable t) {
                policyChain.failWith(io.gravitee.policy.api.PolicyResult.failure(t.getMessage()));
            }
        }

        return null;
    }

    private String executeStreamScript(Request request, Response response, ExecutionContext executionContext,
                                       String script) throws PolicyFailureException {
        // Get script class
        Class<?> scriptClass = getOrCreate(script);

        // Prepare binding
        Binding binding = new Binding();
        binding.setVariable(REQUEST_VARIABLE_NAME, request);
        binding.setVariable(RESPONSE_VARIABLE_NAME, response);
        binding.setVariable(CONTEXT_VARIABLE_NAME, executionContext);
        binding.setVariable(RESULT_VARIABLE_NAME, new PolicyResult());

        // And run script
        Script gScript = InvokerHelper.createScript(scriptClass, binding);
        String content = (String) gScript.run();

        PolicyResult result = (PolicyResult) binding.getVariable(RESULT_VARIABLE_NAME);
        if (result.getState() == PolicyResult.State.FAILURE) {
            throw new PolicyFailureException(result);
        }

        return content;
    }

    private Class<?> getOrCreate(String script) throws CompilationFailedException {
        String key = Sha1.sha1(script);
        return sources.computeIfAbsent(key, s -> {
            GroovyCodeSource gcs = new GroovyCodeSource(script, key, GroovyShell.DEFAULT_CODE_BASE);
            return GROOVY_SHELL.getClassLoader().parseClass(gcs, true);
        });
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
