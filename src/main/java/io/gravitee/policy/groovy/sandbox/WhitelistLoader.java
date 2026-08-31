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

import io.gravitee.common.util.EnvironmentUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Turns whitelist declarations into a {@link Whitelist}.
 * <p/>
 * Declarations come from the built-in groovy-whitelist file on the classpath, from the 'groovy.whitelist.list'
 * configuration, or from both — 'groovy.whitelist.mode' decides whether the configured list completes the built-in one
 * or replaces it.
 * <p/>
 * Reflection is still what resolves a declaration: it is the only way to expand a 'class' declaration to every member
 * it covers, and it is what makes a parameter type render on this side exactly as it does when the sandbox resolves a
 * call. But the resolved members are turned into signatures line by line and never retained, so nothing here holds on
 * to a classloader once loading is over.
 *
 * @author GraviteeSource Team
 */
@Slf4j
final class WhitelistLoader {

    static final String WHITELIST_MODE = "append";
    static final String WHITELIST_MODE_KEY = "groovy.whitelist.mode";
    static final String WHITELIST_LIST_KEY = "groovy.whitelist.list";
    static final String WHITELIST_METHOD_PREFIX = "method ";
    static final String WHITELIST_FIELD_PREFIX = "field ";
    static final String WHITELIST_CLASS_PREFIX = "class ";
    static final String WHITELIST_CONSTRUCTOR_PREFIX = "new ";
    static final String WHITELIST_ANNOTATION_PREFIX = "annotation ";

    private static final String BUILT_IN_WHITELIST = "/groovy-whitelist";

    private final Set<String> methods = new HashSet<>();
    private final Set<String> fields = new HashSet<>();
    private final Set<String> constructors = new HashSet<>();
    private final Set<String> annotationNames = new HashSet<>();

    static Whitelist load(@Nullable Environment environment) {
        WhitelistLoader loader = new WhitelistLoader();
        boolean loadBuiltInWhitelist = true;

        // Load groovy-whitelist from configuration.
        if (environment != null) {
            // Built-in groovy-whitelist will not be loaded if mode is not 'append' (ie: set to 'replace').
            loadBuiltInWhitelist = WHITELIST_MODE.equals(environment.getProperty(WHITELIST_MODE_KEY, WHITELIST_MODE));

            EnvironmentUtils.getPropertiesStartingWith((ConfigurableEnvironment) environment, WHITELIST_LIST_KEY)
                .values()
                .forEach(declaration -> loader.parse(String.valueOf(declaration)));
        }

        // Load built-in groovy-whitelist if required.
        if (loadBuiltInWhitelist) {
            loader.parseBuiltIn();
        }

        return new Whitelist(loader.methods, loader.fields, loader.constructors, loader.annotationNames);
    }

    private void parseBuiltIn() {
        InputStream input = SecuredResolver.class.getResourceAsStream(BUILT_IN_WHITELIST);

        if (input == null) {
            log.error("Groovy built-in groovy-whitelist is missing from the classpath, no member is allowed by default");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String declaration;

            while ((declaration = reader.readLine()) != null) {
                parse(declaration);
            }
        } catch (IOException ioe) {
            log.error("Unable to read Groovy built-in groovy-whitelist", ioe);
        }
    }

    private void parse(String declaration) {
        try {
            if (declaration.startsWith(WHITELIST_METHOD_PREFIX)) {
                methods.add(Whitelist.signatureOf(parseMethod(declaration)));
            } else if (declaration.startsWith(WHITELIST_FIELD_PREFIX)) {
                fields.add(Whitelist.signatureOf(parseField(declaration)));
            } else if (declaration.startsWith(WHITELIST_CONSTRUCTOR_PREFIX)) {
                constructors.add(Whitelist.signatureOf(parseConstructor(declaration)));
            } else if (declaration.startsWith(WHITELIST_ANNOTATION_PREFIX)) {
                annotationNames.addAll(Whitelist.namesOf(parseAnnotation(declaration)));
            } else if (declaration.startsWith(WHITELIST_CLASS_PREFIX)) {
                String clazzName = declaration.split(" ")[1];

                addAll(clazzName, Class::getDeclaredMethods, Whitelist::signatureOf, methods);
                addAll(clazzName, Class::getDeclaredFields, Whitelist::signatureOf, fields);
                addAll(clazzName, Class::getDeclaredConstructors, Whitelist::signatureOf, constructors);
            }
        } catch (Exception e) {
            log.warn("The Groovy whitelisted declaration [{}] cannot be loaded. Message is [{}]", declaration, e.toString());
        }
    }

    /**
     * Expands a 'class' declaration to the signatures of one kind of member.
     * <p/>
     * A missing transitive dependency is reported and skipped rather than losing the whole declaration: the other kinds
     * of member of that class, and every later declaration, still load.
     */
    private static <T> void addAll(
        String clazzName,
        Function<Class<?>, T[]> memberAccessor,
        Function<T, String> signature,
        Set<String> signatures
    ) throws ClassNotFoundException {
        try {
            Class<?> clazz = ClassUtils.forName(clazzName, WhitelistLoader.class.getClassLoader());

            for (T member : memberAccessor.apply(clazz)) {
                signatures.add(signature.apply(member));
            }
        } catch (NoClassDefFoundError e) {
            log.error("Unable to load members from class [{}], a transitive dependency is missing: {}", clazzName, e.getMessage());
        }
    }

    private static Method parseMethod(String declaration) throws ClassNotFoundException, NoSuchMethodException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];
        String methodName = split[2];
        String[] methodArgs = {};

        if (split.length > 3) {
            methodArgs = Arrays.copyOfRange(split, 3, split.length);
        }

        Class<?>[] argumentClasses = getArgumentClasses(methodArgs);

        Class<?> clazz = ClassUtils.forName(clazzName, WhitelistLoader.class.getClassLoader());

        return clazz.getDeclaredMethod(methodName, argumentClasses);
    }

    private static Field parseField(String declaration) throws ClassNotFoundException, NoSuchFieldException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];
        String fieldName = split[2];
        Class<?> clazz = ClassUtils.forName(clazzName, WhitelistLoader.class.getClassLoader());

        return clazz.getDeclaredField(fieldName);
    }

    private static Constructor<?> parseConstructor(String declaration) throws ClassNotFoundException, NoSuchMethodException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];
        String[] args = {};

        if (split.length > 2) {
            args = Arrays.copyOfRange(split, 2, split.length);
        }

        Class<?>[] argumentClasses = getArgumentClasses(args);

        Class<?> clazz = ClassUtils.forName(clazzName, WhitelistLoader.class.getClassLoader());

        return clazz.getDeclaredConstructor(argumentClasses);
    }

    private static Class<?> parseAnnotation(String declaration) throws ClassNotFoundException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];

        return ClassUtils.forName(clazzName, WhitelistLoader.class.getClassLoader());
    }

    private static Class<?>[] getArgumentClasses(String[] args) throws ClassNotFoundException {
        Class<?>[] argumentClasses = new Class<?>[args.length];

        for (int i = 0; i < args.length; i++) {
            argumentClasses[i] = ClassUtils.forName(args[i], WhitelistLoader.class.getClassLoader());
        }

        return argumentClasses;
    }
}
