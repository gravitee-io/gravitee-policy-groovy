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

    private static final GroovyShell GROOVY_SHELL = new GroovyShell();

    private static final ConcurrentMap<String, Class<?>> sources = new ConcurrentHashMap<>();

    public GroovyPolicy(GroovyPolicyConfiguration groovyPolicyConfiguration) {
        this.groovyPolicyConfiguration = groovyPolicyConfiguration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext executionContext, PolicyChain policyChain) {
        executeScript(groovyPolicyConfiguration.getOnRequestScript(), request, response, executionContext, policyChain);
    }

    @OnResponse
    public void onResponse(Request request, Response response, ExecutionContext executionContext, PolicyChain policyChain) {
        executeScript(groovyPolicyConfiguration.getOnResponseScript(), request, response, executionContext, policyChain);
    }

    @OnResponseContent
    public ReadWriteStream onResponseContent(Request request, Response response, ExecutionContext executionContext) {
        String script = groovyPolicyConfiguration.getOnResponseContentScript();

        if (script != null && !script.trim().isEmpty()) {
            return TransformableResponseStreamBuilder
                    .on(response)
                    .transform(
                            buffer -> {
                                try {
                                    // Get script class
                                    Class<?> scriptClass = getOrCreate(script);

                                    // Prepare binding
                                    Binding binding = new Binding();
                                    binding.setVariable("request", new ContentAwareRequest(request, null));
                                    binding.setVariable("context", executionContext);
                                    binding.setVariable("response", new ContentAwareResponse(response, buffer.toString()));

                                    // And run script
                                    Script gScript = InvokerHelper.createScript(scriptClass, binding);
                                    String newContent = (String) gScript.run();

                                    return Buffer.buffer(newContent);
                                } catch (Exception ex) {
                                    throw new TransformationException("Unable to run Groovy script: " + ex.getMessage(), ex);
                                }

                            }
                    ).build();
        }

        return null;
    }

    @OnRequestContent
    public ReadWriteStream onRequestContent(Request request, Response response, ExecutionContext executionContext) {
        String script = groovyPolicyConfiguration.getOnRequestContentScript();

        if (script != null && !script.trim().isEmpty()) {
            return TransformableRequestStreamBuilder
                    .on(request)
                    .transform(
                            buffer -> {
                                try {
                                    // Get script class
                                    Class<?> scriptClass = getOrCreate(script);

                                    // Prepare binding
                                    Binding binding = new Binding();
                                    binding.setVariable("request", new ContentAwareRequest(request, buffer.toString()));
                                    binding.setVariable("response", new ContentAwareResponse(response, null));
                                    binding.setVariable("context", executionContext);

                                    // And run script
                                    Script gScript = InvokerHelper.createScript(scriptClass, binding);
                                    String newContent = (String) gScript.run();

                                    return Buffer.buffer(newContent);
                                } catch (Exception ex) {
                                    throw new TransformationException("Unable to run Groovy script: " + ex.getMessage(), ex);
                                }

                            }
                    ).build();
        }

        return null;
    }

    private void executeScript(String script, Request request, Response response, ExecutionContext executionContext, PolicyChain policyChain) {
        if (script == null || script.trim().isEmpty()) {
            policyChain.doNext(request, response);
        } else {
            try {
                // Get script class
                Class<?> scriptClass = getOrCreate(script);

                // Prepare binding
                Binding binding = new Binding();
                binding.setVariable("response", new ContentAwareResponse(response, null));
                binding.setVariable("request", new ContentAwareRequest(request, null));
                binding.setVariable("context", executionContext);
                binding.setVariable("result", new PolicyResult());

                // And run script
                Script gScript = InvokerHelper.createScript(scriptClass, binding);
                gScript.run();

                PolicyResult result = (PolicyResult) binding.getVariable("result");

                if (result.getState() == PolicyResult.State.SUCCESS) {
                    policyChain.doNext(request, response);
                } else {
                    policyChain.failWith(io.gravitee.policy.api.PolicyResult.failure(
                            result.getCode(),
                            result.getError()
                    ));
                }
            } catch (Exception ex) {
                policyChain.failWith(io.gravitee.policy.api.PolicyResult.failure(ex.getMessage()));
            }
        }
    }

    private Class<?> getOrCreate(String script) throws CompilationFailedException {
        String key = Sha1.sha1(script);
        return sources.computeIfAbsent(key, s -> {
            GroovyCodeSource gcs = new GroovyCodeSource(script, key, GroovyShell.DEFAULT_CODE_BASE);
            return GROOVY_SHELL.getClassLoader().parseClass(gcs, true);
        });
    }
}
