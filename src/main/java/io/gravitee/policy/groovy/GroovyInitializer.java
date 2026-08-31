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
package io.gravitee.policy.groovy;

import io.gravitee.policy.api.PolicyContext;
import io.gravitee.policy.api.PolicyContextProvider;
import io.gravitee.policy.api.PolicyContextProviderAware;
import io.gravitee.policy.groovy.sandbox.SecuredResolver;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.env.Environment;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroovyInitializer implements PolicyContext, PolicyContextProviderAware {

    /**
     * How many activations currently rely on the sandbox, counted among those that share this class. Whichever way the
     * gateway lays its classloaders out, only the last deactivation of a group sharing this counter releases the
     * whitelist, so an undeployment cannot take it away from a deployment still serving traffic.
     * <p/>
     * Should the gateway be confirmed to hand every deployment its own copy of this class, the counter could never
     * exceed one and this whole mechanism could go, along with the test that exercises it.
     */
    private static final AtomicInteger ACTIVATIONS = new AtomicInteger();

    private Environment environment;
    private boolean classLoaderLegacyMode = true;

    @Override
    public void onActivation() {
        ACTIVATIONS.incrementAndGet();

        if (classLoaderLegacyMode || !SecuredResolver.isInitialized()) {
            SecuredResolver.initialize(this.environment);
        }
    }

    @Override
    public void onDeactivation() {
        // Floored at zero so that a deactivation without a matching activation cannot make the counter go negative and
        // keep the whitelist alive for ever.
        int remaining = ACTIVATIONS.updateAndGet(activations -> Math.max(0, activations - 1));

        if (classLoaderLegacyMode && remaining == 0) {
            SecuredResolver.destroy();
        }
    }

    @Override
    public void setPolicyContextProvider(PolicyContextProvider policyContextProvider) {
        this.environment = policyContextProvider.getComponent(Environment.class);
        this.classLoaderLegacyMode = environment.getProperty("classloader.legacy.enabled", Boolean.class, true);
    }
}
