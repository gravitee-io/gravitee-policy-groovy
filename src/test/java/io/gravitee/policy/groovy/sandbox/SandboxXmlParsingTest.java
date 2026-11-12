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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

        return new SecuredGroovyShell().evaluate(script, binding);
    }

    @Test
    void externalEntityCannotReadALocalFile() {
        String script = """
            def s = new groovy.xml.XmlSlurper(false, true, true)
            s.setProperty('http://javax.xml.XMLConstants/property/accessExternalDTD', 'all')
            return s.parseText('<!DOCTYPE r [<!ENTITY xxe SYSTEM "' + secretUri + '">]><r>&xxe;</r>').text()
            """;

        assertThatThrownBy(() -> evaluate(script)).isInstanceOf(SecurityException.class);
    }

    @Test
    void constructorEnablingDocTypeDeclarationIsRejected() {
        assertThatThrownBy(() -> evaluate("new groovy.xml.XmlSlurper(false, true, true)")).isInstanceOf(SecurityException.class);
    }

    @Test
    void parserPropertyCannotBeChanged() {
        assertThatThrownBy(() -> evaluate("new groovy.xml.XmlSlurper().setProperty('a', 'b')")).isInstanceOf(SecurityException.class);
    }

    @Test
    void parserFeatureCannotBeChanged() {
        assertThatThrownBy(() -> evaluate("new groovy.xml.XmlSlurper().setFeature('a', true)")).isInstanceOf(SecurityException.class);
    }

    @Test
    void entityResolverCannotBeReplaced() {
        assertThatThrownBy(() -> evaluate("new groovy.xml.XmlSlurper().setEntityResolver(resolver)")).isInstanceOf(SecurityException.class);
    }

    @Test
    void documentCannotBeFetchedFromAUri() {
        assertThatThrownBy(() -> evaluate("new groovy.xml.XmlSlurper().parse(secretUri)")).isInstanceOf(SecurityException.class);
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
}
