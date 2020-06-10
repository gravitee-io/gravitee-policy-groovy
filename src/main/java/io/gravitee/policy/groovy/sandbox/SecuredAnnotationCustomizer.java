/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.groovy.sandbox;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

/**
 * Groovy compilation customizer allowing to restrict annotations used in Groovy scripts.
 * This customizer acts at compilation phase and delegates all verifications to {@link SecuredResolver} instance.
 *
 * @see SecuredResolver
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecuredAnnotationCustomizer extends CompilationCustomizer {

    public SecuredAnnotationCustomizer() {
        super(CompilePhase.CONVERSION);
    }

    @Override
    public void call(final SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
        new RejectAnnotationVisitor(source).visitClass(classNode);
    }

    private static class RejectAnnotationVisitor extends ClassCodeVisitorSupport {
        private SourceUnit source;

        public RejectAnnotationVisitor(SourceUnit source) {
            this.source = source;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return source;
        }

        @Override
        public void visitAnnotations(AnnotatedNode node) {

            for (AnnotationNode an : node.getAnnotations()) {
                if(!SecuredResolver.getInstance().isAnnotationAllowed(an.getClassNode().getName())) {
                    throw new SecurityException("Annotation " + an.getClassNode().getName() + " cannot be used in the sandbox.");
                }
            }
        }
    }
}
