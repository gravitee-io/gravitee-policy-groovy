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
import groovy.transform.TimedInterrupt;
import io.gravitee.policy.groovy.GroovyPolicy;
import io.gravitee.policy.groovy.utils.Sha1;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.groovy.json.internal.FastStringUtils;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;
import org.kohsuke.groovy.sandbox.SandboxTransformer;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class SecuredGroovyShell {

    /** Maximum script execution time in seconds; clamped to [{@value #SCRIPT_TIMEOUT_MIN_SECONDS}, {@value #SCRIPT_TIMEOUT_MAX_SECONDS}]. */
    static final String SCRIPT_TIMEOUT_PROPERTY = "gravitee.policy.groovy.script.timeout.seconds";

    static final long SCRIPT_TIMEOUT_DEFAULT_SECONDS = 5L;
    static final long SCRIPT_TIMEOUT_MIN_SECONDS = 1L;
    static final long SCRIPT_TIMEOUT_MAX_SECONDS = 30L;

    /**
     * Number of hours to keep compiled script in cache after the last time it was accessed.
     */
    private static final int CODE_CACHE_EXPIRATION_HOURS = 1;

    static {
        // Do not change this block of code which is required to work with the classloader used
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

        long scriptTimeoutSeconds = resolveScriptTimeoutSeconds();
        log.debug("Groovy script execution timeout set to {} second(s) (property: {})", scriptTimeoutSeconds, SCRIPT_TIMEOUT_PROPERTY);

        Map<String, Object> timedInterruptParams = Map.of(
            "value",
            scriptTimeoutSeconds,
            "unit",
            new PropertyExpression(GeneralUtils.classX(TimeUnit.class), "SECONDS")
        );
        conf.addCompilationCustomizers(new ASTTransformationCustomizer(timedInterruptParams, TimedInterrupt.class));

        this.groovyShell = new GroovyShell(conf);

        // Create a groovy interceptor to intercept all calls and check if they are allowed or not.
        this.groovyInterceptor = new SecuredInterceptor();
    }

    static long resolveScriptTimeoutSeconds() {
        long value = Long.getLong(SCRIPT_TIMEOUT_PROPERTY, SCRIPT_TIMEOUT_DEFAULT_SECONDS);
        return Math.max(SCRIPT_TIMEOUT_MIN_SECONDS, Math.min(SCRIPT_TIMEOUT_MAX_SECONDS, value));
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
        return Maybe.<T>fromCallable(() -> evaluate(key, script, binding))
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation());
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
