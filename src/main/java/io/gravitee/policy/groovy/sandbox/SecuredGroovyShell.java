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
package io.gravitee.policy.groovy.sandbox;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.gravitee.policy.groovy.GroovyPolicy;
import io.gravitee.policy.groovy.utils.Sha1;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Duration;
import org.apache.groovy.json.internal.FastStringUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;
import org.kohsuke.groovy.sandbox.SandboxTransformer;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecuredGroovyShell {

    /**
     * Number of hours to keep compiled script in cache after the last time it was accessed.
     */
    private static final int CODE_CACHE_EXPIRATION_HOURS = 1;

    static {
        // Do not change this block of code which is required to work with groovy 2.5 and the classloader used
        // to load services
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(GroovyPolicy.class.getClassLoader());
        FastStringUtils.toCharArray("hack");
        Thread.currentThread().setContextClassLoader(loader);
    }

    private final GroovyShell groovyShell;
    private final Cache<String, Class<?>> sources;
    private final GroovyInterceptor groovyInterceptor;

    public SecuredGroovyShell() {
        this.sources = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofHours(CODE_CACHE_EXPIRATION_HOURS)).build();

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

    /**
     * Useful to pre-compile a given script or to check if a script compiles correctly.
     *
     * @param script the script to compile.
     *
     * @throws CompilationFailedException in case the script does not compile.
     */
    public void compile(String script) throws CompilationFailedException {
        getOrCreate(getKey(script), script);
    }

    public <T> T evaluate(String script, Binding binding) {
        return evaluate(getKey(script), script, binding);
    }

    public <T> Maybe<T> evaluateRx(String script, Binding binding) {
        final String key = getKey(script);
        final boolean compiled = sources.getIfPresent(key) != null;
        final Maybe<@NonNull T> groovyEval = Maybe.fromCallable(() -> evaluate(key, script, binding));

        if (!compiled) {
            return groovyEval.subscribeOn(Schedulers.io()).observeOn(Schedulers.computation());
        }

        return groovyEval;
    }

    private <T> T evaluate(String key, String script, Binding binding) {
        try {
            this.groovyInterceptor.register();

            // Get script class.
            Class<?> scriptClass = getOrCreate(key, script);

            // And run script.
            Script gScript = InvokerHelper.createScript(scriptClass, binding);

            return (T) gScript.run();
        } finally {
            this.groovyInterceptor.unregister();
        }
    }

    private String getKey(String script) {
        return Sha1.sha1(script);
    }

    private Class<?> getOrCreate(String key, String script) throws CompilationFailedException {
        try {
            return sources.get(key, () -> {
                GroovyCodeSource gcs = new GroovyCodeSource(script, key, GroovyShell.DEFAULT_CODE_BASE);
                return groovyShell.getClassLoader().parseClass(gcs, true);
            });
        } catch (Exception e) {
            final Throwable cause = e.getCause();
            if (cause instanceof CompilationFailedException) {
                throw (CompilationFailedException) cause;
            } else if (cause instanceof SecurityException) {
                throw (SecurityException) cause;
            }
            throw new IllegalStateException("Unable to compile script", e);
        }
    }
}
