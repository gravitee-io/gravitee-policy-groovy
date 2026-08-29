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

/**
 * A class the whitelist knows nothing about, used to check that a Groovy script cannot make one of its methods
 * reachable by declaring a class of the same name. It exists only to be denied.
 *
 * @author GraviteeSource Team
 */
public class CacheCollisionProbe {

    public String probe() {
        return "from java";
    }
}
