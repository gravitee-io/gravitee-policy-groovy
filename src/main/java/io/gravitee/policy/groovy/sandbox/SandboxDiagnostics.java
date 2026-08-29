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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;

/**
 * Reports the anomalous states that can make {@link SecuredResolver} deny a whitelisted member (APIM-14800).
 * <p/>
 * The failure is intermittent, isolated to a single gateway instance and only cleared by recycling it, and several
 * distinct code paths can produce it. This class exists to tell them apart from production logs, so it observes and
 * never decides: it is called on the denial path only, and takes no part in any authorization outcome.
 * <p/>
 * Its output goes to the dedicated <code>io.gravitee.policy.groovy.sandbox.diagnostics</code> logger, so that support
 * can raise its verbosity without touching the rest of the policy.
 *
 * @author GraviteeSource Team
 */
class SandboxDiagnostics {

    /** The runtime class is absent from the whitelist index although another class with the same name is in it. */
    static final String MARKER_CLASSLOADER_MISMATCH = "[APIM-14800/classloader-mismatch]";

    /** The denial was served from the cache, whose keys carry no classloader identity. */
    static final String MARKER_CACHED_DENIAL = "[APIM-14800/cached-denial]";

    /** The whitelist was (re)built by the lazy getInstance() path rather than by GroovyInitializer.onActivation(). */
    static final String MARKER_LAZY_INITIALIZATION = "[APIM-14800/lazy-initialization]";

    private static final Logger log = LoggerFactory.getLogger("io.gravitee.policy.groovy.sandbox.diagnostics");

    /**
     * Once an anomaly occurs it usually occurs on every single request. Reports are therefore deduplicated, and the
     * number of distinct reports an already degraded gateway can accumulate is bounded.
     */
    private static final int MAX_REPORTED_ANOMALIES = 500;

    private static final AtomicInteger GENERATIONS = new AtomicInteger();

    private final int generation;
    private final Map<String, Class<?>> whitelistedClassesByName;
    private final Set<String> reportedAnomalies = ConcurrentHashMap.newKeySet();

    SandboxDiagnostics(Whitelist whitelist) {
        this.generation = GENERATIONS.incrementAndGet();
        this.whitelistedClassesByName = indexByName(
            whitelist.methodsByType().keySet(),
            whitelist.fieldsByType().keySet(),
            whitelist.constructorsByType().keySet()
        );

        log.info(
            "Groovy sandbox whitelist generation {} loaded by [{}]: {} types with methods, {} with fields, {} with constructors, {} annotations",
            generation,
            describe(SecuredResolver.class.getClassLoader()),
            whitelist.methodsByType().size(),
            whitelist.fieldsByType().size(),
            whitelist.constructorsByType().size(),
            whitelist.annotations().size()
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
     * Describes the state the sandbox was in when it denied a member. Called on the denial path only; the outcome is
     * already decided and is never affected by what happens here.
     *
     * @param deniedClass the runtime class the member was resolved against.
     * @param cacheKey the key the decision was stored under, which carries the class name but no classloader identity.
     * @param fromCache whether the denial was served from the cache rather than freshly computed.
     */
    void reportDenial(Class<?> deniedClass, String cacheKey, boolean fromCache) {
        if (!log.isWarnEnabled()) {
            return;
        }

        Class<?> whitelisted = whitelistedClassesByName.get(deniedClass.getName());

        if (whitelisted != null && whitelisted != deniedClass) {
            report(
                MARKER_CLASSLOADER_MISMATCH,
                deniedClass,
                "{} [{}] denied although it is whitelisted: the runtime class comes from [{}] whereas the whitelist was " +
                    "built from [{}], and the resolver was loaded by [{}]",
                deniedClass.getName(),
                describe(deniedClass.getClassLoader()),
                describe(whitelisted.getClassLoader()),
                describe(SecuredResolver.class.getClassLoader())
            );
            return;
        }

        if (fromCache && log.isDebugEnabled()) {
            log.debug(
                "{} [{}] denied from cache key [{}], which carries no classloader identity",
                MARKER_CACHED_DENIAL,
                deniedClass.getName(),
                cacheKey
            );
        }
    }

    /**
     * Emits the first occurrence of an anomaly as a warning, and the repeats as debug, so that a gateway stuck in a
     * degraded state does not drown its own logs.
     */
    private void report(String marker, Class<?> deniedClass, String message, Object... arguments) {
        boolean firstOccurrence = isFirstOccurrence(marker, deniedClass);

        if (!firstOccurrence && !log.isDebugEnabled()) {
            return;
        }

        Object[] markedArguments = new Object[arguments.length + 1];
        markedArguments[0] = marker;
        System.arraycopy(arguments, 0, markedArguments, 1, arguments.length);

        if (firstOccurrence) {
            log.warn(message, markedArguments);
        } else {
            log.debug(message, markedArguments);
        }
    }

    private boolean isFirstOccurrence(String marker, Class<?> deniedClass) {
        if (reportedAnomalies.size() >= MAX_REPORTED_ANOMALIES) {
            return false;
        }

        return reportedAnomalies.add(marker + '|' + deniedClass.getName() + '|' + describe(deniedClass.getClassLoader()));
    }

    @SafeVarargs
    private static Map<String, Class<?>> indexByName(Set<Class<?>>... whitelistedTypes) {
        Map<String, Class<?>> byName = new HashMap<>();

        for (Set<Class<?>> types : whitelistedTypes) {
            for (Class<?> type : types) {
                byName.putIfAbsent(type.getName(), type);
            }
        }

        return Map.copyOf(byName);
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
