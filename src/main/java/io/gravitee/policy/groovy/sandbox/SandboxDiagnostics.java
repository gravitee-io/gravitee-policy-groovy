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

import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;

/**
 * Traces the life of the Groovy sandbox whitelist.
 * <p/>
 * The defects behind the intermittent sandbox denials were hard to pin down partly because nothing said when the
 * whitelist was built, rebuilt or dropped. This keeps that visible: a gateway that keeps rebuilding its whitelist, or
 * that rebuilds it outside of the policy activation, is worth looking at.
 * <p/>
 * Output goes to the dedicated <code>io.gravitee.policy.groovy.sandbox.diagnostics</code> logger, so that support can
 * raise its verbosity without touching the rest of the policy.
 *
 * @author GraviteeSource Team
 */
class SandboxDiagnostics {

    /** The whitelist was (re)built by the lazy getInstance() path rather than by GroovyInitializer.onActivation(). */
    static final String MARKER_LAZY_INITIALIZATION = "[groovy-sandbox/lazy-initialization]";

    private static final Logger log = LoggerFactory.getLogger("io.gravitee.policy.groovy.sandbox.diagnostics");

    private static final AtomicInteger GENERATIONS = new AtomicInteger();

    private final int generation;

    SandboxDiagnostics(Whitelist whitelist) {
        this.generation = GENERATIONS.incrementAndGet();

        log.info(
            "Groovy sandbox whitelist generation {} loaded by [{}]: {} methods, {} fields, {} constructors, {} annotation names",
            generation,
            describe(SecuredResolver.class.getClassLoader()),
            whitelist.methods().size(),
            whitelist.fields().size(),
            whitelist.constructors().size(),
            whitelist.annotationNames().size()
        );
    }

    /**
     * Reports a whitelist built through {@link SecuredResolver#getInstance()} instead of
     * {@link io.gravitee.policy.groovy.GroovyInitializer#onActivation()}, which means the previous one was dropped
     * while the policy was still in use. The configured environment is reused, so nothing is lost — but a gateway that
     * keeps rebuilding its whitelist is paying for it, and wants to know.
     */
    static void lazyInitialization(@Nullable Environment environment) {
        log.warn(
            "{} Groovy sandbox whitelist rebuilt outside of the policy activation (configured environment reused: {})",
            MARKER_LAZY_INITIALIZATION,
            environment != null
        );
    }

    static void destroyed(int destroyedGeneration) {
        log.info("Groovy sandbox whitelist generation {} destroyed", destroyedGeneration);
    }

    int generation() {
        return generation;
    }

    /**
     * Describes a classloader by its own name when it has one — plugin classloaders do, and it tells which API a class
     * came from — and always by its identity, since two classloaders sharing a name is precisely the situation to
     * detect.
     */
    private static String describe(@Nullable ClassLoader classLoader) {
        if (classLoader == null) {
            return "bootstrap";
        }

        String name = classLoader.getName();
        String identity = classLoader.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(classLoader));

        return name == null ? identity : name + " " + identity;
    }
}
