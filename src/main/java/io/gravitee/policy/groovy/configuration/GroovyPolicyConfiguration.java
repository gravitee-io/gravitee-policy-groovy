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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.gravitee.policy.api.PolicyConfiguration;
import java.util.List;
import java.util.stream.Stream;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroovyPolicyConfiguration implements PolicyConfiguration {

    private boolean readContent;

    private boolean overrideContent;

    private String script;

    private String onRequestScript;

    private String onResponseScript;

    private String onRequestContentScript;

    private String onResponseContentScript;

    /**
     * This getter is overridden for backward compatibility.
     *
     * For v3 API, we assume that the policy will override the content if the script is an on{PHASE}Content script,
     * where {PHASE} can be either Request or Response.
     *
     * For v4 API, overriding content is an explicit configuration property.
     *
     * @return whether the policy should override the content or not
     */
    public boolean isOverrideContent() {
        return overrideContent || isNotBlank(onRequestContentScript) || isNotBlank(onResponseContentScript);
    }

    /**
     * This getter is used for backward compatibility.
     *
     * If `script` is defined, it means that all other scripts are not (this property is not available for v3 API).
     *
     * If `script` is not defined, we return either one or both of the on{PHASE} and on{PHASE}Content
     * scripts, where {PHASE} can be either Request or Response.
     *
     * When running both  on${PHASE} and on${PHASE}Content scrips, the order ensures
     * that overriding the content will be done using the on${PHASE}Content script.
     *
     * @return the list of scripts to run for a v3 API, a singleton list with the script for a v4 API.
     */
    public List<String> getScripts() {
        return Stream.of(script, onRequestScript, onRequestContentScript, onResponseScript, onResponseContentScript)
            .filter(StringUtils::isNotBlank)
            .toList();
    }
}
