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
package io.gravitee.policy.groovy.sandbox;

import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import groovy.transform.TimedInterrupt;
import io.gravitee.policy.groovy.GroovyPolicy;
import io.gravitee.policy.groovy.utils.Sha1;
import org.apache.groovy.json.internal.FastStringUtils;
import org.apache.groovy.util.Maps;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;
import org.kohsuke.groovy.sandbox.SandboxTransformer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecuredGroovyShell {

    static {
        // Do not change this block of code which is required to work with groovy 2.5 and the classloader used
        // to load services
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(GroovyPolicy.class.getClassLoader());
        FastStringUtils.toCharArray("hack");
        Thread.currentThread().setContextClassLoader(loader);
    }

    private GroovyShell groovyShell;
    private ConcurrentMap<String, Class<?>> sources = new ConcurrentHashMap<>();
    private GroovyInterceptor groovyInterceptor;

    public SecuredGroovyShell() {

        CompilerConfiguration conf = new CompilerConfiguration();

        // Add Kohsuke's sandbox transformer which will delegate calls to SecuredInterceptor.
        conf.addCompilationCustomizers(new SandboxTransformer());

        // Avoid use of some groovy annotations that could lead to security issues.
        conf.addCompilationCustomizers(new SecuredAnnotationCustomizer());

        // Use built-in secure customizer only to disallow package directive.
        SecureASTCustomizer secureASTCustomizer = new SecureASTCustomizer();
        secureASTCustomizer.setPackageAllowed(false);
        conf.addCompilationCustomizers(secureASTCustomizer);

        // TODO: Use time interrupt to avoid long running scripts.
        // Note: groovy scripts are currently run on vertx threads. Should be executed outside vertx to prevent possible server crash.
        // ASTTransformationCustomizer timeInterrupt = new ASTTransformationCustomizer(Maps.of("value", 1L, "unit", propX(classX(TimeUnit.class), "SECONDS")), TimedInterrupt.class);
        // conf.addCompilationCustomizers(timeInterrupt);

        this.groovyShell = new GroovyShell(conf);

        // Create a groovy interceptor to intercept all calls and check if they are allowed or not.
        this.groovyInterceptor = new SecuredInterceptor();
    }

    public <T> T evaluate(String script, Binding binding) {

        try {
            this.groovyInterceptor.register();

            // Get script class.
            Class<?> scriptClass = getOrCreate(script);

            // And run script.
            Script gScript = InvokerHelper.createScript(scriptClass, binding);

            return (T) gScript.run();
        } finally {
            this.groovyInterceptor.unregister();
        }
    }

    private Class<?> getOrCreate(String script) throws CompilationFailedException {

        String key = Sha1.sha1(script);

        return sources.computeIfAbsent(key, s -> {
            GroovyCodeSource gcs = new GroovyCodeSource(script, key, GroovyShell.DEFAULT_CODE_BASE);
            return groovyShell.getClassLoader().parseClass(gcs, true);
        });
    }
}
