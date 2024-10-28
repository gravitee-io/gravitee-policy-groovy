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
package io.gravitee.policy.groovy.model;

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.reactive.api.context.GenericExecutionContext;
import io.gravitee.gateway.reactive.api.context.GenericRequest;
import io.gravitee.gateway.reactive.api.context.GenericResponse;
import io.gravitee.reporter.api.v4.metric.Metrics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
@Value
public class BindableExecutionContext {

    private static final String CONTEXT_DICTIONARIES_VARIABLE = "dictionaries";

    GenericExecutionContext executionContext;

    @SuppressWarnings({ "unchecked", "unused" })
    public Map<String, String> getDictionaries() {
        return (Map<String, String>) executionContext
            .getTemplateEngine()
            .getTemplateContext()
            .lookupVariable(CONTEXT_DICTIONARIES_VARIABLE);
    }

    public GenericRequest request() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    public GenericResponse response() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    public Metrics metrics() {
        return executionContext.metrics();
    }

    public Metrics getMetrics() {
        return executionContext.metrics();
    }

    public void setAttribute(String s, Object o) {
        executionContext.setAttribute(s, o);
    }

    public void putAttribute(String s, Object o) {
        executionContext.putAttribute(s, o);
    }

    public void removeAttribute(String s) {
        executionContext.removeAttribute(s);
    }

    public <T> T getAttribute(String s) {
        return executionContext.getAttribute(s);
    }

    public <T> List<T> getAttributeAsList(String s) {
        return executionContext.getAttributeAsList(s);
    }

    public Set<String> getAttributeNames() {
        return executionContext.getAttributeNames();
    }

    public <T> Map<String, T> getAttributes() {
        return executionContext.getAttributes();
    }
}
