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
public class BindableExecutionContext implements GenericExecutionContext {

    private static final String CONTEXT_DICTIONARIES_VARIABLE = "dictionaries";

    GenericExecutionContext executionContext;

    @SuppressWarnings({ "unchecked", "unused" })
    public Map<String, String> getDictionaries() {
        return (Map<String, String>) executionContext
            .getTemplateEngine()
            .getTemplateContext()
            .lookupVariable(CONTEXT_DICTIONARIES_VARIABLE);
    }

    @Override
    public GenericRequest request() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public GenericResponse response() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public Metrics metrics() {
        return executionContext.metrics();
    }

    public Metrics getMetrics() {
        return executionContext.metrics();
    }

    @Override
    public <T> T getComponent(Class<T> aClass) {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public void setAttribute(String s, Object o) {
        executionContext.setAttribute(s, o);
    }

    @Override
    public void putAttribute(String s, Object o) {
        executionContext.putAttribute(s, o);
    }

    @Override
    public void removeAttribute(String s) {
        executionContext.removeAttribute(s);
    }

    @Override
    public <T> T getAttribute(String s) {
        return executionContext.getAttribute(s);
    }

    @Override
    public <T> List<T> getAttributeAsList(String s) {
        return executionContext.getAttributeAsList(s);
    }

    @Override
    public Set<String> getAttributeNames() {
        return executionContext.getAttributeNames();
    }

    @Override
    public <T> Map<String, T> getAttributes() {
        return executionContext.getAttributes();
    }

    @Override
    public void setInternalAttribute(String s, Object o) {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public void putInternalAttribute(String s, Object o) {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public void removeInternalAttribute(String s) {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public <T> T getInternalAttribute(String s) {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public <T> Map<String, T> getInternalAttributes() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }

    @Override
    public TemplateEngine getTemplateEngine() {
        throw new UnsupportedOperationException("Groovy scripts do not support accessing this method");
    }
}
