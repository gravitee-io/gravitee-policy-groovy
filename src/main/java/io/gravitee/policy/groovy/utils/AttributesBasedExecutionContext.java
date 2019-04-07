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
package io.gravitee.policy.groovy.utils;

import io.gravitee.gateway.api.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AttributesBasedExecutionContext {

    private final static String CONTEXT_DICTIONARIES_VARIABLE = "dictionaries";
    private final ExecutionContext context;
    private final Map<String, Object> attributes = new AttributeMap();

    public AttributesBasedExecutionContext(final ExecutionContext context) {
        this.context = context;
    }

    public Map<String, Map<String, String>> getDictionaries() {
        return (Map<String, Map<String, String>>)
                this.context.getTemplateEngine().getTemplateContext().lookupVariable(CONTEXT_DICTIONARIES_VARIABLE);
    }

    public Object getAttributes() {
        return attributes;
    }

    private class AttributeMap extends HashMap<String, Object> {

        AttributeMap() { }

        @Override
        public Object get(Object key) {
            return context.getAttribute((String) key);
        }
    }

}