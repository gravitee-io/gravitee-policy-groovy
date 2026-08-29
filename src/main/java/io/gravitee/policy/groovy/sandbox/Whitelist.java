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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.lang.Nullable;

/**
 * What the Groovy sandbox is allowed to reach, as an immutable value.
 * <p/>
 * Members are held as textual signatures rather than as reflective objects, for two reasons. Identity comparison made
 * the whitelist blind to a class loaded by a different classloader than the one it was built from, which is the defect
 * reported in APIM-14800: the very same class, reached through another classloader, was denied. And holding
 * {@link Class} objects pinned the classloader that defined them, which is what the JVM-wide destroy() existed to work
 * around.
 * <p/>
 * The trade-off is deliberate: a class carrying a whitelisted name inherits its permissions whichever classloader
 * defined it. Exploiting that already requires the ability to place a class on the gateway classpath, and classes
 * declared inside a Groovy script are unconditionally allowed anyway. This is also what jenkins script-security does.
 * <p/>
 * Immutability is what makes it safe to hand a resolver out without a lock: a thread that sees a
 * {@link SecuredResolver} sees a whitelist that is complete and that no one can empty under its feet. Replacing the
 * whitelist means building another instance, never mutating this one.
 *
 * @author GraviteeSource Team
 */
record Whitelist(Set<String> methods, Set<String> fields, Set<String> constructors, Set<String> annotationNames) {
    Whitelist {
        methods = Set.copyOf(methods);
        fields = Set.copyOf(fields);
        constructors = Set.copyOf(constructors);
        annotationNames = Set.copyOf(annotationNames);
    }

    /**
     * @param method the resolved method, null when none matched the arguments.
     */
    boolean allows(@Nullable Method method) {
        return method != null && methods.contains(signatureOf(method));
    }

    /**
     * @param field the resolved field, null when the class declares none by that name.
     */
    boolean allows(@Nullable Field field) {
        return field != null && fields.contains(signatureOf(field));
    }

    /**
     * @param constructor the resolved constructor, null when none matched the arguments.
     */
    boolean allows(@Nullable Constructor<?> constructor) {
        return constructor != null && constructors.contains(signatureOf(constructor));
    }

    /**
     * Annotations are matched by name because that is all a Groovy {@code AnnotationNode} gives us, so every way of
     * naming the same annotation is indexed.
     */
    boolean allowsAnnotation(String name) {
        return annotationNames.contains(name);
    }

    static String signatureOf(Method method) {
        return signatureOf(method.getDeclaringClass().getName(), method.getName(), method);
    }

    static String signatureOf(Constructor<?> constructor) {
        return signatureOf(constructor.getDeclaringClass().getName(), "<init>", constructor);
    }

    static String signatureOf(Field field) {
        return field.getDeclaringClass().getName() + '#' + field.getName();
    }

    static Set<String> namesOf(Class<?> annotation) {
        Set<String> names = new LinkedHashSet<>();

        names.add(annotation.getName());
        names.add(annotation.getSimpleName());
        names.add(annotation.getTypeName());

        if (annotation.getCanonicalName() != null) {
            names.add(annotation.getCanonicalName());
        }

        return names;
    }

    /**
     * Both sides — the declarations parsed at load time and the members resolved at call time — derive the signature
     * from a reflective object, so the rendering of parameter types is identical on both.
     */
    private static String signatureOf(String declaringType, String name, Executable executable) {
        StringJoiner parameters = new StringJoiner(",", declaringType + '#' + name + '(', ")");

        for (Class<?> parameterType : executable.getParameterTypes()) {
            parameters.add(parameterType.getName());
        }

        return parameters.toString();
    }
}
