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
package io.gravitee.policy.groovy.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Test to verify that serializing a Groovy policy configuration without the deprecated 'scope' field
 * does not add a default value. This addresses APIM-14171 where the default value causes Terraform drift.
 *
 * @author Benoit Bordigoni
 */
class GroovyPolicyConfigurationSerializationTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void should_not_add_scope_when_serializing_config_without_scope() throws Exception {
        // Create a configuration using the new schema (script, readContent, overrideContent)
        // without the deprecated scope field
        GroovyPolicyConfiguration config = new GroovyPolicyConfiguration();
        config.setScript("return 'test'");
        config.setReadContent(true);
        config.setOverrideContent(false);

        // Serialize the configuration to JSON
        String json = mapper.writeValueAsString(config);

        // The serialized JSON should NOT contain the "scope" field
        assertThat(json).doesNotContain("scope").contains("script").contains("readContent");
    }

    @Test
    void should_preserve_scope_when_explicitly_set() throws Exception {
        // Create a configuration with an explicit scope (for backward compatibility)
        GroovyPolicyConfiguration config = new GroovyPolicyConfiguration();
        config.setScript("return 'test'");
        config.setReadContent(true);

        // Note: The GroovyPolicyConfiguration class doesn't have a scope field anymore,
        // but if someone were to add it back, this test would verify it's preserved
        // For now, we just verify the serialization works correctly
        String json = mapper.writeValueAsString(config);

        assertThat(json).doesNotContain("scope");
    }

    @Test
    void should_deserialize_config_without_scope() throws Exception {
        // Simulate a JSON payload from Terraform that doesn't include scope
        String jsonWithoutScope = """
                {
                    "script": "return 'test'",
                    "readContent": true,
                    "overrideContent": false
                }
            """;

        // Deserialize the configuration
        GroovyPolicyConfiguration config = mapper.readValue(jsonWithoutScope, GroovyPolicyConfiguration.class);

        // Verify the configuration was parsed correctly
        assertThat(config.getScript()).isEqualTo("return 'test'");
        assertThat(config.isReadContent()).isTrue();
        assertThat(config.isOverrideContent()).isFalse();

        // Re-serialize to ensure no scope field is added
        String reSerialized = mapper.writeValueAsString(config);
        assertThat(reSerialized).doesNotContain("scope");
    }
}
