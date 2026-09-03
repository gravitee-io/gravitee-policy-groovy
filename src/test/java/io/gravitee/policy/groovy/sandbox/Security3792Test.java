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
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SECURITY-3792 regression test. A typed for-each loop, {@code for (T v in collection)}, made Groovy
 * emit a per-element cast to {@code T} with no AST node for the sandbox to intercept, so a script
 * could construct a non-whitelisted class through the loop variable's type — while the equivalent
 * declaration was already blocked. These tests pin both halves of the fix: the escape is now blocked,
 * and legitimate typed loops keep working.
 */
class Security3792Test {

    @BeforeEach
    void setUp() {
        SecuredResolver.destroy();
        SecuredResolver.initialize(null);
    }

    private Object evaluate(String script) {
        return new SecuredGroovyShell().evaluate(script, new Binding());
    }

    @Test
    void typedForEachCannotConstructNonWhitelistedClass() {
        assertThatThrownBy(() -> evaluate("for (java.io.FileOutputStream o : [['/tmp/security-3792']]) { }")).isInstanceOf(
            SecurityException.class
        );
        assertThatThrownBy(() -> evaluate("for (java.io.File f : [['/tmp', 'security-3792']]) { }")).isInstanceOf(SecurityException.class);
    }

    @Test
    void legitimateTypedForEachIsPreserved() {
        assertThat((Object) evaluate("def out = []; for (String s : ['a', 'b']) { out.add(s.toUpperCase()) }; return out")).isEqualTo(
            List.of("A", "B")
        );
        assertThat((Object) evaluate("def out = []; for (BigDecimal d : [1, 2]) { out.add(d) }; return out")).isEqualTo(
            List.of(new BigDecimal(1), new BigDecimal(2))
        );
        assertThat((Object) evaluate("def sum = 0; for (int i : [1, 2, 3]) { sum += i }; return sum")).isEqualTo(6);
    }
}
