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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import groovy.lang.Binding;
import io.gravitee.policy.groovy.model.http.BindableHttpRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/**
 * Acceptance tests for the APIM-14800 diagnostics instrumentation.
 * <p/>
 * The sandbox intermittently denies a whitelisted property (typically <code>request.content</code>) on a gateway
 * instance, and the reporter could not reproduce it end-to-end. Several distinct code paths in {@link SecuredResolver}
 * can produce that symptom, so before changing any authorization logic we instrument the denial path well enough to
 * tell them apart from production logs.
 * <p/>
 * These tests assert on the diagnostics log output only: the instrumentation must never change an authorization
 * decision. The behavioural safety net remains {@link SecuredGroovyShellTest}, which must stay untouched and green.
 *
 * @author GraviteeSource Team
 */
class SandboxDiagnosticsTest {

    private static final String DIAGNOSTICS_LOGGER = "io.gravitee.policy.groovy.sandbox.diagnostics";

    /** Markers carried by the diagnostics messages, duplicated here on purpose so the tests do not depend on production constants. */
    private static final String CLASSLOADER_MISMATCH = "[APIM-14800/classloader-mismatch]";

    private static final String WHITELIST_INCOMPLETE = "[APIM-14800/whitelist-incomplete]";
    private static final String CACHED_DENIAL = "[APIM-14800/cached-denial]";
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
    @DisplayName("H2: a whitelisted class loaded by a foreign classloader is denied and reported with both classloader identities")
    void shouldReportClassloaderMismatchOnDenial() throws Exception {
        SecuredResolver.initialize(null);
        appender.list.clear();

        Class<?> foreign = foreignCopyOfBindableHttpRequest();
        assertThat(foreign).isNotSameAs(BindableHttpRequest.class);
        assertThat(foreign.getName()).isEqualTo(BINDABLE_HTTP_REQUEST);

        SecuredResolver.getInstance().isGetPropertyAllowed(foreign, "content");

        assertThat(messages(Level.WARN))
            .singleElement()
            .satisfies(message -> {
                assertThat(message).contains(CLASSLOADER_MISMATCH).contains(BINDABLE_HTTP_REQUEST);
                assertThat(message)
                    .as("both the runtime and the whitelist classloader identities must be reported")
                    .contains(classLoaderIdentity(foreign.getClassLoader()))
                    .contains(classLoaderIdentity(BindableHttpRequest.class.getClassLoader()));
            });
    }

    @Test
    @DisplayName("H4: a denial served from the FQCN-keyed cache is reported as such")
    void shouldReportCachedDenial() throws Exception {
        SecuredResolver.initialize(null);
        Class<?> foreign = foreignCopyOfBindableHttpRequest();

        // Poison the cache: the foreign class is denied and cached under a key built from the FQCN only.
        assertThat(SecuredResolver.getInstance().isGetPropertyAllowed(foreign, "content")).isFalse();
        appender.list.clear();

        // Documents the current defect: the legitimate class now inherits the cached denial.
        // This assertion is expected to fail once the resolver itself is fixed, which is the point.
        assertThat(SecuredResolver.getInstance().isGetPropertyAllowed(BindableHttpRequest.class, "content")).isFalse();

        assertThat(messages(Level.DEBUG)).anySatisfy(message ->
            assertThat(message).contains(CACHED_DENIAL).contains(BINDABLE_HTTP_REQUEST)
        );
    }

    @Test
    @DisplayName("H1: a denial evaluated against an empty whitelist is reported as an incomplete whitelist")
    void shouldReportIncompleteWhitelistOnDenial() {
        SecuredResolver.initialize(null);
        SecuredResolver resolver = SecuredResolver.getInstance();

        // Reproduces the visibility window of the startup race: destroy() clears the static maps while a thread still
        // holds a reference to the instance it obtained from the unsynchronized getInstance().
        SecuredResolver.destroy();
        appender.list.clear();

        resolver.isGetPropertyAllowed(BindableHttpRequest.class, "content");

        assertThat(messages(Level.WARN)).anySatisfy(message ->
            assertThat(message).contains(WHITELIST_INCOMPLETE).contains(BINDABLE_HTTP_REQUEST)
        );
    }

    @Test
    @DisplayName("H3: an initialization triggered by the lazy getInstance() path is reported")
    void shouldReportLazyInitialization() {
        appender.list.clear();

        SecuredResolver.getInstance();

        assertThat(messages(Level.WARN)).anySatisfy(message -> assertThat(message).contains(LAZY_INITIALIZATION));
    }

    @Test
    @DisplayName("H3: the lazy initialization report says when a configured whitelist has just been lost")
    void shouldReportConfiguredWhitelistLossOnLazyInitialization() {
        SecuredResolver.initialize(new MockEnvironment().withProperty(WHITELIST_LIST_KEY + "[0]", "class java.lang.String"));
        SecuredResolver.destroy();
        appender.list.clear();

        SecuredResolver.getInstance();

        assertThat(messages(Level.WARN)).anySatisfy(message ->
            assertThat(message).contains(LAZY_INITIALIZATION).contains(WHITELIST_LIST_KEY)
        );
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

    @Test
    @DisplayName("Repeated denials on the same class are reported once, so a degraded gateway does not flood its logs")
    void shouldReportEachAnomalyOnce() throws Exception {
        SecuredResolver.initialize(null);
        appender.list.clear();

        Class<?> foreign = foreignCopyOfBindableHttpRequest();
        for (String property : List.of("content", "path", "host", "uri", "content")) {
            SecuredResolver.getInstance().isGetPropertyAllowed(foreign, property);
        }

        assertThat(messages(Level.WARN))
            .as("the classloader mismatch is a per-class anomaly, not a per-property one")
            .singleElement()
            .satisfies(message -> assertThat(message).contains(CLASSLOADER_MISMATCH));
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
