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
package io.gravitee.policy.groovy.configuration;

import io.gravitee.policy.api.PolicyConfiguration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroovyPolicyConfiguration implements PolicyConfiguration {

    private String onRequestScript;

    private String onResponseScript;

    private String onResponseContentScript;

    private String onRequestContentScript;

    public String getOnRequestScript() {
        return onRequestScript;
    }

    public void setOnRequestScript(String onRequestScript) {
        this.onRequestScript = onRequestScript;
    }

    public String getOnResponseContentScript() {
        return onResponseContentScript;
    }

    public void setOnResponseContentScript(String onResponseContentScript) {
        this.onResponseContentScript = onResponseContentScript;
    }

    public String getOnResponseScript() {
        return onResponseScript;
    }

    public void setOnResponseScript(String onResponseScript) {
        this.onResponseScript = onResponseScript;
    }

    public String getOnRequestContentScript() {
        return onRequestContentScript;
    }

    public void setOnRequestContentScript(String onRequestContentScript) {
        this.onRequestContentScript = onRequestContentScript;
    }
}
