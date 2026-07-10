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

import groovy.transform.TimedInterrupt;
import java.util.HashMap;
import java.util.Map;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

/**
 * Applies the {@link TimedInterrupt} transformation class by class, skipping interfaces.
 *
 * <p>The {@link TimedInterrupt} transformation injects instance fields into every class to track the script
 * expiration time. Instance fields are illegal on interfaces, so any script declaring an interface (or an
 * annotation) fails to compile when the transformation is applied module-wide. Interfaces hold no executable
 * code to instrument, so skipping them does not weaken the execution timeout.</p>
 *
 * @author GraviteeSource Team
 */
public class InterfaceSafeTimedInterruptCustomizer extends CompilationCustomizer {

    private final ASTTransformationCustomizer delegate;

    public InterfaceSafeTimedInterruptCustomizer(Map<String, Object> annotationParameters) {
        this(new ASTTransformationCustomizer(perClassParameters(annotationParameters), TimedInterrupt.class));
    }

    private InterfaceSafeTimedInterruptCustomizer(ASTTransformationCustomizer delegate) {
        super(delegate.getPhase());
        this.delegate = delegate;
    }

    /**
     * Each class node is annotated individually; applying the transformation to all classes of the module
     * would reach the interfaces this customizer intends to skip.
     */
    private static Map<String, Object> perClassParameters(Map<String, Object> annotationParameters) {
        Map<String, Object> parameters = new HashMap<>(annotationParameters);
        parameters.put("applyToAllClasses", false);
        return parameters;
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
        if (!classNode.isInterface()) {
            delegate.call(source, context, classNode);
        }
    }
}
