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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import groovy.lang.Binding;
import java.io.File;
import java.util.List;
import org.codehaus.groovy.runtime.MethodClosure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for two chainable whitelist gaps. {@code asType()} reached a target's constructor through
 * trusted {@code DefaultGroovyMethods} code, bypassing {@code onNewInstance}, and a {@code MethodClosure} routed
 * the whitelisted {@code Closure.call} to an arbitrary method; chained, they instantiated and invoked
 * non-whitelisted classes. These tests pin both halves with benign targets ({@link File}, no OS command) and
 * assert that the legitimate uses keep working.
 */
class SandboxWhitelistBypassTest {

    @BeforeEach
    void setUp() {
        SecuredResolver.destroy();
        SecuredResolver.initialize(null);
    }

    private Object evaluate(String script) {
        return evaluate(script, new Binding());
    }

    private Object evaluate(String script, Binding binding) {
        return new SecuredGroovyShell().evaluate(script, binding);
    }

    // --- asType() reaching a non-whitelisted constructor ---

    @Test
    void asTypeMethodCallCannotReachNonWhitelistedConstructor() {
        // 'as' is already blocked; this covers the explicit asType() method-call form.
        assertThatThrownBy(() -> evaluate("('/tmp/x').asType(java.io.File)")).isInstanceOf(SecurityException.class);
    }

    @Test
    void asTypeWithCollectionSpreadCannotReachNonWhitelistedConstructor() {
        assertThatThrownBy(() -> evaluate("(['/tmp', 'x']).asType(java.io.File)")).isInstanceOf(SecurityException.class);
    }

    @Test
    void asTypeCannotBuildAMethodClosure() {
        // The bridge between the two gaps: building a raw MethodClosure through asType.
        assertThatThrownBy(() -> evaluate("([1, 'toString']).asType(org.codehaus.groovy.runtime.MethodClosure)")).isInstanceOf(
            SecurityException.class
        );
    }

    @Test
    void legitimateAsTypeConversionsArePreserved() {
        assertThat((Object) evaluate("('5').asType(Integer)")).isEqualTo(5);
        assertThat((Object) evaluate("('abc').asType(List)")).isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    void asTypeIsANoOpWhenSourceAlreadyMatchesTarget() {
        // Source already an instance of the target: castToType returns it unchanged, so nothing is constructed.
        assertThat((Object) evaluate("'x'.asType(Object)")).isEqualTo("x");
        assertThat((Object) evaluate("[1].asType(Object)")).isEqualTo(List.of(1));
        assertThat((Object) evaluate("({ -> 1 }).asType(Closure)")).isInstanceOf(groovy.lang.Closure.class);
    }

    @Test
    void standardJdkCollectionConversionsArePreserved() {
        assertThat((Object) evaluate("[1, 2, 2].asType(HashSet)")).isEqualTo(java.util.Set.of(1, 2));
        assertThat((Object) evaluate("[3, 1].asType(TreeSet)")).isEqualTo(new java.util.TreeSet<>(List.of(1, 3)));
        assertThat((Object) evaluate("[1, 2].asType(LinkedList)")).isEqualTo(List.of(1, 2));
        assertThat((Object) evaluate("(1..3).asType(ArrayList)")).isEqualTo(List.of(1, 2, 3));
        assertThat((Object) evaluate("[b: 1, a: 2].asType(TreeMap)")).isEqualTo(java.util.Map.of("a", 2, "b", 1));
    }

    @Test
    void asTypeToNonWhitelistedConcreteCollectionSubtypeIsBlocked() {
        // java.util.concurrent is outside the exempt java.util collection targets, so it is a real construction.
        assertThatThrownBy(() -> evaluate("[1].asType(java.util.concurrent.CopyOnWriteArrayList)")).isInstanceOf(SecurityException.class);
    }

    // --- a MethodClosure routing Closure.call to a non-whitelisted method ---

    @Test
    void rawMethodClosureCallIsReChecked() {
        // A raw MethodClosure over a non-whitelisted method must be checked as if the method were called directly.
        Binding binding = new Binding();
        binding.setVariable("mc", new MethodClosure(new File("/tmp/x"), "getAbsolutePath"));

        assertThatThrownBy(() -> evaluate("mc.call()", binding)).isInstanceOf(SecurityException.class);
    }

    @Test
    void methodClosureToWhitelistedMethodIsPreserved() {
        Binding binding = new Binding();
        binding.setVariable("mc", new MethodClosure("abc", "toUpperCase"));

        assertThat((Object) evaluate("mc.call()", binding)).isEqualTo("ABC");
    }

    @Test
    void methodPointerOperatorKeepsWorkingForWhitelistedMethods() {
        assertThat((Object) evaluate("def c = 'abc'.&toUpperCase; c.call()")).isEqualTo("ABC");
    }

    @Test
    void methodClosureToWhitelistedMethodWorksWithArgumentsAndDoCall() {
        assertThat((Object) evaluate("def c = 'hello'.&substring; c(1)")).isEqualTo("ello");
        assertThat((Object) evaluate("def c = 'hello'.&substring; c.doCall(1)")).isEqualTo("ello");
    }

    @Test
    void rawMethodClosureDoCallIsReChecked() {
        // doCall() must be re-checked like call().
        Binding binding = new Binding();
        binding.setVariable("mc", new MethodClosure(new File("/tmp/x"), "getAbsolutePath"));

        assertThatThrownBy(() -> evaluate("mc.doCall()", binding)).isInstanceOf(SecurityException.class);
    }

    @Test
    void methodPointerOperatorStillBlocksNonWhitelistedMethods() {
        // getClass() is not whitelisted; the '&' form was already blocked and must stay blocked.
        assertThatThrownBy(() -> evaluate("def c = 'abc'.&getClass; c.call()")).isInstanceOf(SecurityException.class);
    }
}
