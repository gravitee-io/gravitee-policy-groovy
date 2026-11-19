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
package io.gravitee.policy.groovy.model.http;

import io.gravitee.gateway.api.buffer.Buffer;
import java.util.Base64;
import javax.annotation.Nullable;

/**
 * Body of an HTTP request or response, exposed to scripts as text, bytes or base64.
 * A {@code null} buffer means "Read content" is disabled, and every accessor throws.
 *
 * @author GraviteeSource Team
 */
final class BindableHttpContent {

    private final Buffer buffer;

    private final String disabledMessage;

    private String content;

    BindableHttpContent(@Nullable Buffer buffer, String disabledMessage) {
        this.buffer = buffer;
        this.disabledMessage = disabledMessage;
    }

    String asString() {
        if (content == null) {
            content = requireBuffer().toString();
        }
        return content;
    }

    byte[] asByteArray() {
        return requireBuffer().getBytes();
    }

    String asBase64() {
        return Base64.getEncoder().encodeToString(asByteArray());
    }

    private Buffer requireBuffer() {
        if (buffer == null) {
            throw new UnsupportedOperationException(disabledMessage);
        }
        return buffer;
    }
}
