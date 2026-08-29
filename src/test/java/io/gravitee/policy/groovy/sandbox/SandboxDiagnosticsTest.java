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

import static io.gravitee.policy.groovy.sandbox.WhitelistLoader.WHITELIST_LIST_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import groovy.lang.Binding;
import io.gravitee.policy.api.PolicyContextProvider;
import io.gravitee.policy.groovy.GroovyInitializer;
import io.gravitee.policy.groovy.model.http.BindableHttpRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Acceptance tests for APIM-14800.
 * <p/>
 * The sandbox intermittently denied a whitelisted property — typically <code>request.content</code> — on a single
 * gateway instance, and only recycling it helped. These tests pin down the behaviours that made that possible: a class
 * reached through a foreign classloader must be judged like the class the whitelist was built from, a resolver handed
 * out before a destroy() must keep working, and a configured whitelist must survive a rebuild.
 * <p/>
 * The behavioural safety net remains {@link SecuredGroovyShellTest}, which must stay untouched and green: it is what
 * proves these fixes do not widen what the sandbox allows.
 *
 * @author GraviteeSource Team
 */
class SandboxDiagnosticsTest {

    private static final String DIAGNOSTICS_LOGGER = "io.gravitee.policy.groovy.sandbox.diagnostics";

    /** Marker carried by the diagnostics messages, duplicated here on purpose so the tests do not depend on production constants. */
    private static final String LAZY_INITIALIZATION = "[APIM-14800/lazy-initialization]";

    private static final String BINDABLE_HTTP_REQUEST = BindableHttpRequest.class.getName();

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private Logger diagnosticsLogger;

    @BeforeEach
    void setUp() {
        diagnosticsLogger = (Logger) LoggerFactory.getLogger(DIAGNOSTICS_LOGGER);
        diagnosticsLogger.setLevel(Level.DEBUG);
        appender.start();
        diagnosticsLogger.addAppender(appender);

        SecuredResolver.destroy();
    }

    @AfterEach
    void tearDown() {
        diagnosticsLogger.detachAppender(appender);
        appender.stop();

        // Leave a healthy resolver behind for the other test classes.
        SecuredResolver.destroy();
        SecuredResolver.initialize(null);
    }

    @Test
    @DisplayName("A whitelisted class reached through a foreign classloader is allowed, whichever order it is reached in")
    void shouldAllowAWhitelistedClassLoadedByAForeignClassloader() throws Exception {
        SecuredResolver.initialize(null);
        appender.list.clear();

        Class<?> foreign = foreignCopyOfBindableHttpRequest();
        assertThat(foreign).isNotSameAs(BindableHttpRequest.class);
        assertThat(foreign.getName()).isEqualTo(BINDABLE_HTTP_REQUEST);

        // Foreign first: this is the order that used to deny, and then poison the cache for the legitimate class.
        assertThat(SecuredResolver.getInstance().isGetPropertyAllowed(foreign, "content")).isTrue();
        assertThat(SecuredResolver.getInstance().isGetPropertyAllowed(BindableHttpRequest.class, "content")).isTrue();

        assertThat(messages(Level.WARN)).isEmpty();
    }

    @Test
    @DisplayName("Reaching the legitimate class first does not change the outcome for a foreign copy")
    void shouldAllowAForeignCopyReachedAfterTheLegitimateClass() throws Exception {
        SecuredResolver.initialize(null);
        appender.list.clear();

        Class<?> foreign = foreignCopyOfBindableHttpRequest();

        assertThat(SecuredResolver.getInstance().isGetPropertyAllowed(BindableHttpRequest.class, "content")).isTrue();
        assertThat(SecuredResolver.getInstance().isGetPropertyAllowed(foreign, "content")).isTrue();

        assertThat(messages(Level.WARN)).isEmpty();
    }

    @Test
    @DisplayName("A resolver obtained before destroy() keeps working against a complete whitelist")
    void shouldKeepServingAResolverObtainedBeforeDestroy() {
        SecuredResolver.initialize(null);
        SecuredResolver resolver = SecuredResolver.getInstance();

        // This is the startup race window: a thread holding a resolver while another destroys it must not suddenly see
        // an empty whitelist and start denying everything.
        SecuredResolver.destroy();
        appender.list.clear();

        assertThat(resolver.isGetPropertyAllowed(BindableHttpRequest.class, "content")).isTrue();
        assertThat(messages(Level.WARN)).isEmpty();
    }

    @Test
    @DisplayName("A whitelist rebuilt outside of the policy activation is still traced, since it is worth knowing about")
    void shouldReportLazyInitialization() {
        appender.list.clear();

        SecuredResolver.getInstance();

        assertThat(messages(Level.WARN)).anySatisfy(message -> assertThat(message).contains(LAZY_INITIALIZATION));
    }

    @Test
    @DisplayName("A whitelist configured through the environment survives destroy() and lazy re-initialization")
    void shouldKeepTheConfiguredWhitelistAcrossDestroy() {
        SecuredResolver.initialize(null);
        assertThat(SecuredResolver.getInstance().isMethodAllowed(StringJoiner.class, "add", "x"))
            .as("the probe must not be reachable through the built-in whitelist, otherwise this test proves nothing")
            .isFalse();
        SecuredResolver.destroy();

        SecuredResolver.initialize(new MockEnvironment().withProperty(WHITELIST_LIST_KEY + "[0]", "class java.util.StringJoiner"));
        assertThat(SecuredResolver.getInstance().isMethodAllowed(StringJoiner.class, "add", "x")).isTrue();

        SecuredResolver.destroy();

        assertThat(SecuredResolver.getInstance().isMethodAllowed(StringJoiner.class, "add", "x"))
            .as("the configured whitelist must not be silently replaced by the built-in one")
            .isTrue();
    }

    @Test
    @DisplayName("An environment given to a later initialize() is applied, not merely recorded")
    void shouldApplyAnEnvironmentGivenAfterAFirstInitialize() {
        SecuredResolver.initialize(null);
        assertThat(SecuredResolver.getInstance().isMethodAllowed(StringJoiner.class, "add", "x"))
            .as("the probe must not be reachable through the built-in whitelist, otherwise this test proves nothing")
            .isFalse();

        // No destroy() in between: a configuration must not have to wait for one to take effect.
        SecuredResolver.initialize(new MockEnvironment().withProperty(WHITELIST_LIST_KEY + "[0]", "class java.util.StringJoiner"));

        assertThat(SecuredResolver.getInstance().isMethodAllowed(StringJoiner.class, "add", "x")).isTrue();
    }

    @Test
    @DisplayName("Undeploying one API does not take the sandbox away from the others still serving traffic")
    void shouldKeepTheSandboxUsableWhileAnotherDeploymentNeedsIt() {
        GroovyInitializer first = initializer();
        GroovyInitializer second = initializer();

        first.onActivation();
        second.onActivation();
        first.onDeactivation();

        assertThat(SecuredResolver.isInitialized()).isTrue();

        second.onDeactivation();
    }

    private static GroovyInitializer initializer() {
        PolicyContextProvider provider = mock(PolicyContextProvider.class);
        when(provider.getComponent(Environment.class)).thenReturn(new MockEnvironment());

        GroovyInitializer initializer = new GroovyInitializer();
        initializer.setPolicyContextProvider(provider);

        return initializer;
    }

    @Test
    @DisplayName("An ordinary denial of a genuinely forbidden call produces no diagnostics warning")
    void shouldNotWarnOnOrdinaryDenial() {
        SecuredResolver.initialize(null);
        appender.list.clear();

        assertThatExceptionOfType(SecurityException.class).isThrownBy(() ->
            new SecuredGroovyShell().evaluate("\"ls -l\".execute().text", new Binding())
        );

        assertThat(messages(Level.WARN)).isEmpty();
    }

    private List<String> messages(Level level) {
        return appender.list
            .stream()
            .filter(event -> event.getLevel() == level)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    private static String classLoaderIdentity(ClassLoader classLoader) {
        return Integer.toHexString(System.identityHashCode(classLoader));
    }

    private static Class<?> foreignCopyOfBindableHttpRequest() throws ClassNotFoundException {
        return new ForeignClassLoader(SandboxDiagnosticsTest.class.getClassLoader(), BINDABLE_HTTP_REQUEST).loadClass(
            BINDABLE_HTTP_REQUEST
        );
    }

    /**
     * A parent-last classloader for a handful of classes only: it redefines them from the bytecode served by its
     * parent, and delegates everything else. The redefined classes therefore share their fully qualified name with the
     * ones the whitelist was built from, but not their identity — which is exactly the situation APIM-14800 describes.
     */
    private static final class ForeignClassLoader extends ClassLoader {

        private final Set<String> redefined;

        private ForeignClassLoader(ClassLoader parent, String... classNames) {
            super("foreign", parent);
            this.redefined = Set.of(classNames);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!redefined.contains(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);

                if (loaded == null) {
                    byte[] bytecode = readBytecode(name);
                    loaded = defineClass(name, bytecode, 0, bytecode.length);
                }

                if (resolve) {
                    resolveClass(loaded);
                }

                return loaded;
            }
        }

        private byte[] readBytecode(String name) throws ClassNotFoundException {
            try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }
                return input.readAllBytes();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
