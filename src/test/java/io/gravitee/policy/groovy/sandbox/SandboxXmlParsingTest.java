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
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXParseException;

/**
 * XmlSlurper was whitelisted as a whole class, which exposed the members that turn on DOCTYPE processing and external
 * entity resolution, so a script could read local files. These tests pin that those members are rejected and that the
 * default, DOCTYPE-free parsing keeps working.
 */
class SandboxXmlParsingTest {

    private static final String SECRET = "not-for-scripts";

    @TempDir
    Path tempDir;

    private Path secretFile;

    @BeforeEach
    void setUp() throws Exception {
        SecuredResolver.destroy();
        SecuredResolver.initialize(null);
        secretFile = Files.writeString(tempDir.resolve("secret.txt"), SECRET);
    }

    private Object evaluate(String script) {
        Binding binding = new Binding();
        binding.setVariable("secretUri", secretFile.toUri().toString());
        binding.setVariable("resolver", (EntityResolver) (publicId, systemId) -> null);
        binding.setVariable("xmlStream", new ByteArrayInputStream("<r>ok</r>".getBytes(StandardCharsets.UTF_8)));
        binding.setVariable("xmlReader", new StringReader("<r>ok</r>"));

        return new SecuredGroovyShell().evaluate(script, binding);
    }

    @Test
    void externalEntityCannotReadALocalFile() {
        String script = """
            def s = new groovy.xml.XmlSlurper()
            s.setFeature('http://apache.org/xml/features/disallow-doctype-decl', false)
            s.setProperty('http://javax.xml.XMLConstants/property/accessExternalDTD', 'all')
            return s.parseText('<!DOCTYPE r [<!ENTITY xxe SYSTEM "' + secretUri + '">]><r>&xxe;</r>').text()
            """;

        assertThatThrownBy(() -> evaluate(script))
            .isInstanceOf(SecurityException.class)
            .hasMessageNotContaining(SECRET);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "new groovy.xml.XmlSlurper(false, true, true)",
            "[false, true, true].asType(groovy.xml.XmlSlurper)",
            "[false, true, true] as groovy.xml.XmlSlurper",
            "class E extends groovy.xml.XmlSlurper { E() { super(false, true, true) } }; new E()",
            "new groovy.xml.XmlSlurper().setProperty('a', 'b')",
            "new groovy.xml.XmlSlurper().setFeature('a', true)",
            "new groovy.xml.XmlSlurper().setEntityResolver(resolver)",
            "new groovy.xml.XmlSlurper().parse(secretUri)",
        }
    )
    void membersEnablingDocTypeOrEntityResolutionAreRejected(String script) {
        assertThatThrownBy(() -> evaluate(script)).isInstanceOf(SecurityException.class);
    }

    @Test
    void parseTextOfPlainXmlKeepsWorking() {
        String script = """
            def content = new groovy.xml.XmlSlurper().parseText('<user><name>jane</name></user>')
            return content.getAt(0).children().get(0).text()
            """;

        assertThat((Object) evaluate(script)).isEqualTo("jane");
    }

    @Test
    void namespaceAwareConstructorKeepsWorking() {
        String script = "return new groovy.xml.XmlSlurper(false, true).parseText('<r>ok</r>').text()";

        assertThat((Object) evaluate(script)).isEqualTo("ok");
    }

    @Test
    void docTypeIsStillRefusedByTheDefaultParser() {
        String script = "new groovy.xml.XmlSlurper().parseText('<!DOCTYPE r [<!ENTITY x \"y\">]><r>&x;</r>')";

        assertThatThrownBy(() -> evaluate(script)).isInstanceOf(SAXParseException.class);
    }

    @Test
    void streamCanBeParsed() {
        assertThat((Object) evaluate("return new groovy.xml.XmlSlurper().parse(xmlStream).text()")).isEqualTo("ok");
    }

    @Test
    void readerCanBeParsed() {
        assertThat((Object) evaluate("return new groovy.xml.XmlSlurper().parse(xmlReader).text()")).isEqualTo("ok");
    }

    @Test
    void whitespaceHandlingCanBeConfigured() {
        String script = """
            def s = new groovy.xml.XmlSlurper()
            s.setKeepWhitespace(true)
            s.setKeepIgnorableWhitespace(true)
            return s.isKeepIgnorableWhitespace()
            """;

        assertThat((Object) evaluate(script)).isEqualTo(true);
    }
}
