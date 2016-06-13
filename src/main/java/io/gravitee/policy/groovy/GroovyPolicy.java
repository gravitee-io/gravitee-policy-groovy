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
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnResponse;
import io.gravitee.policy.groovy.configuration.GroovyPolicyConfiguration;
import io.gravitee.policy.groovy.utils.Sha1;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author David BRASSELY (david at gravitee.io)
 * @author GraviteeSource Team
 */
public class GroovyPolicy {

    private final GroovyPolicyConfiguration groovyPolicyConfiguration;

    private static final GroovyShell GROOVY_SHELL = new GroovyShell();

    private static ConcurrentMap<String, Class<?>> sources = new ConcurrentHashMap<>();

    public GroovyPolicy(GroovyPolicyConfiguration groovyPolicyConfiguration) {
        this.groovyPolicyConfiguration = groovyPolicyConfiguration;
    }

    @OnResponse
    public void onResponse(Request request, Response response, PolicyChain policyChain) {
        // Get script class
        Class<?> scriptClass = getOrCreate(groovyPolicyConfiguration.getScript());

        // Prepare binding
        Binding binding = new Binding();
        binding.setVariable("response", response);

        // And run script
        Script script = InvokerHelper.createScript(scriptClass, binding);
        script.run();

        policyChain.doNext(request, response);
    }

    private Class<?> getOrCreate(String script) {
        String key = Sha1.sha1(script);
        return sources.computeIfAbsent(key, s -> {
            GroovyCodeSource gcs = new GroovyCodeSource(script, key, GroovyShell.DEFAULT_CODE_BASE);
            return GROOVY_SHELL.getClassLoader().parseClass(gcs, true);
        });
    }
}
