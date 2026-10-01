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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SECURITY-3341 regression test. A cast performed in a {@code this(...)} constructor call let a script
 * construct a non-whitelisted class through the target constructor's parameter type, bypassing the
 * sandbox. These tests pin that the escape is blocked while ordinary scripts (including the initial
 * script instantiation and range operators) keep working.
 */
class Security3341Test {

    @BeforeEach
    void setUp() {
        SecuredResolver.destroy();
        SecuredResolver.initialize(null);
    }

    private Object evaluate(String script) {
        return new SecuredGroovyShell().evaluate(script, new Binding());
    }

    @Test
    void thisConstructorCastCannotConstructNonWhitelistedClass() {
        assertThatThrownBy(() ->
            evaluate(
                "class Pwn { Object s; Pwn() { this(['/tmp/security-3341']) }; Pwn(java.io.FileOutputStream f) { this.s = f } }; new Pwn()"
            )
        ).isInstanceOf(SecurityException.class);
    }

    @Test
    void ordinaryScriptsAndRangesStillWork() {
        assertThat((Object) evaluate("def n = 0; for (x in 0..3) { n += x }; return n")).isEqualTo(6);
        assertThat((Object) evaluate("return (1..5).collect { it * 2 }")).isEqualTo(List.of(2, 4, 6, 8, 10));
    }

    /**
     * Per review: only IntRange construction is gated by 1.34 (hence its whitelist entry). The other
     * range flavours build a NumberRange/ObjectRange and need no Range whitelist entry; this pins that
     * they keep working. (String ranges are left out: their per-element next() is whitelisted on master
     * only, an orthogonal, pre-existing difference unrelated to this bump.)
     */
    @Test
    void everyRangeFlavourWorks() {
        assertThat((Object) evaluate("return (0..3).toList()")).isEqualTo(List.of(0, 1, 2, 3)); // IntRange
        assertThat((Object) evaluate("return (0..<3).toList()")).isEqualTo(List.of(0, 1, 2)); // IntRange, exclusive
        assertThat((Object) evaluate("return (0L..2L).toList()")).isEqualTo(List.of(0L, 1L, 2L)); // NumberRange
        assertThat((Object) evaluate("return (0.0..2.0).size()")).isEqualTo(3); // NumberRange (BigDecimal)
    }
}
