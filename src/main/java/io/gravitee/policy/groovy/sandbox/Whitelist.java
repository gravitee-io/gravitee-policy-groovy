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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * What the Groovy sandbox is allowed to reach, as an immutable value.
 * <p/>
 * Immutability is what makes it safe to hand a resolver out without a lock: a thread that sees a
 * {@link SecuredResolver} sees a whitelist that is complete and that no one can empty under its feet. Replacing the
 * whitelist means building another instance, never mutating this one.
 *
 * @author GraviteeSource Team
 */
record Whitelist(
    Map<Class<?>, List<Method>> methodsByType,
    Map<Class<?>, List<Field>> fieldsByType,
    Map<Class<?>, List<Constructor<?>>> constructorsByType,
    Set<Class<?>> annotations
) {
    Whitelist {
        methodsByType = Map.copyOf(methodsByType);
        fieldsByType = Map.copyOf(fieldsByType);
        constructorsByType = Map.copyOf(constructorsByType);
        annotations = Set.copyOf(annotations);
    }

    List<Method> methodsOf(Class<?> type) {
        return methodsByType.getOrDefault(type, List.of());
    }

    List<Field> fieldsOf(Class<?> type) {
        return fieldsByType.getOrDefault(type, List.of());
    }

    /**
     * @param constructor the resolved constructor, which is null when none matched the arguments — an immutable empty
     *                    list would throw on {@code contains(null)}, so the check has to come first.
     */
    boolean allowsConstructor(Class<?> type, @Nullable Constructor<?> constructor) {
        return constructor != null && constructorsByType.getOrDefault(type, List.of()).contains(constructor);
    }

    /**
     * Annotations are matched by name because that is all a Groovy {@code AnnotationNode} gives us, so every way of
     * naming the same annotation is accepted.
     */
    boolean allowsAnnotation(String name) {
        return annotations
            .stream()
            .anyMatch(
                annotation ->
                    annotation.getCanonicalName().equals(name) ||
                    annotation.getName().equals(name) ||
                    annotation.getSimpleName().equals(name) ||
                    annotation.getTypeName().equals(name)
            );
    }
}
