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
package io.gravitee.policy.v3.groovy;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.groovy.configuration.GroovyPolicyConfiguration;
import io.gravitee.reporter.api.http.Metrics;
import java.io.*;
import java.nio.charset.Charset;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroovyPolicyV3Test {

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private PolicyChain policyChain;

    @Mock
    private GroovyPolicyConfiguration configuration;

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
        when(request.metrics()).thenReturn(Metrics.on(System.currentTimeMillis()).build());
    }

    @Test
    public void shouldFail_invalidScript() throws Exception {
        when(configuration.getOnRequestScript()).thenReturn(loadResource("invalid_script.groovy"));
        new GroovyPolicyV3(configuration).onRequest(request, response, executionContext, policyChain);

        verify(policyChain, times(1)).failWith(any(PolicyResult.class));
    }

    /**
     * Doc example: https://docs.gravitee.io/apim_policies_groovy.html#description
     */
    @Test
    public void shouldModifyResponseHeaders() throws Exception {
        HttpHeaders headers = spy(HttpHeaders.create());
        when(response.headers()).thenReturn(headers);
        when(configuration.getOnRequestScript()).thenReturn(loadResource("modify_response_headers.groovy"));
        new GroovyPolicyV3(configuration).onRequest(request, response, executionContext, policyChain);

        verify(headers, times(1)).remove(eq("X-Powered-By"));
        verify(headers, times(1)).set(eq("X-Gravitee-Gateway-Version"), eq(List.of("0.14.0")));
        verify(policyChain, times(1)).doNext(request, response);
    }

    /**
     * Issue: https://github.com/gravitee-io/issues/issues/2455
     */
    @Test
    public void shouldSetContextAttribute() throws Exception {
        when(configuration.getOnRequestScript()).thenReturn(loadResource("set_context_attribute.groovy"));
        new GroovyPolicyV3(configuration).onRequest(request, response, executionContext, policyChain);

        verify(executionContext, times(1)).setAttribute(eq("anyKey"), eq(0));
        verify(policyChain, times(1)).doNext(request, response);
    }

    /**
     * Doc example: https://docs.gravitee.io/apim_policies_groovy.html#onrequest_onresponse
     * First run does not break the request.
     */
    @Test
    public void shouldNotBreakRequest() throws Exception {
        HttpHeaders headers = spy(HttpHeaders.create());
        when(request.headers()).thenReturn(headers);

        when(configuration.getOnRequestScript()).thenReturn(loadResource("break_request.groovy"));

        new GroovyPolicyV3(configuration).onRequest(request, response, executionContext, policyChain);
        verify(headers, times(1)).set(eq("X-Groovy-Policy"), eq(List.of("ok")));
        verify(policyChain, times(1)).doNext(request, response);
    }

    /**
     * Doc example: https://docs.gravitee.io/apim_policies_groovy.html#onrequest_onresponse
     * Second run must break because of HTTP headers
     */
    @Test
    public void shouldBreakRequest() throws Exception {
        HttpHeaders headers = spy(HttpHeaders.create());
        when(request.headers()).thenReturn(headers);

        when(configuration.getOnRequestScript()).thenReturn(loadResource("break_request.groovy"));

        headers.set("X-Gravitee-Break", "value");
        new GroovyPolicyV3(configuration).onRequest(request, response, executionContext, policyChain);

        verify(policyChain, times(1)).failWith(
            argThat(
                result ->
                    result.statusCode() == HttpStatusCode.INTERNAL_SERVER_ERROR_500 &&
                    result.message().equals("Stop request processing due to X-Gravitee-Break header")
            )
        );
    }

    @Test
    public void shouldReadJson() throws Exception {
        HttpHeaders headers = spy(HttpHeaders.create());
        when(request.headers()).thenReturn(headers);

        when(configuration.getOnRequestContentScript()).thenReturn(loadResource("read_json.groovy"));
        String content = loadResource("read_json.json");

        ReadWriteStream stream = new GroovyPolicyV3(configuration).onRequestContent(request, response, executionContext, policyChain);
        stream.end(Buffer.buffer(content));

        verify(policyChain, never()).failWith(any(PolicyResult.class));
        verify(policyChain, never()).streamFailWith(any(PolicyResult.class));
        verify(policyChain, never()).doNext(any(), any());
    }

    @Test
    public void shouldReadXml() throws Exception {
        HttpHeaders headers = spy(HttpHeaders.create());
        when(request.headers()).thenReturn(headers);

        when(configuration.getOnRequestContentScript()).thenReturn(loadResource("read_xml.groovy"));
        String content = loadResource("read_xml.xml");

        ReadWriteStream stream = new GroovyPolicyV3(configuration).onRequestContent(request, response, executionContext, policyChain);
        stream.end(Buffer.buffer(content));

        verify(policyChain, never()).failWith(any(PolicyResult.class));
        verify(policyChain, never()).streamFailWith(any(PolicyResult.class));
        verify(policyChain, never()).doNext(any(), any());
    }

    @Test
    public void shouldIterateOnMap() throws Exception {
        HttpHeaders headers = spy(HttpHeaders.create());
        when(request.headers()).thenReturn(headers);

        when(configuration.getOnRequestContentScript()).thenReturn(loadResource("iterate_on_map.groovy"));
        String content = loadResource("iterate_on_map.json");

        ReadWriteStream stream = new GroovyPolicyV3(configuration).onRequestContent(request, response, executionContext, policyChain);
        stream.end(Buffer.buffer(content));

        verify(policyChain, never()).failWith(any(PolicyResult.class));
        verify(policyChain, never()).streamFailWith(any(PolicyResult.class));
        verify(policyChain, never()).doNext(any(), any());
    }

    @Test
    public void shouldPlayWithStrings() throws Exception {
        HttpHeaders headers = spy(HttpHeaders.create());
        when(request.headers()).thenReturn(headers);

        when(configuration.getOnRequestContentScript()).thenReturn(loadResource("play_with_strings.groovy"));

        ReadWriteStream stream = new GroovyPolicyV3(configuration).onRequestContent(request, response, executionContext, policyChain);
        stream.end(Buffer.buffer());

        verify(policyChain, never()).failWith(any(PolicyResult.class));
        verify(policyChain, never()).streamFailWith(any(PolicyResult.class));
        verify(policyChain, never()).doNext(any(), any());
    }

    @Test
    public void shouldPlayWithHeadersOnRequest() throws IOException {
        HttpHeaders headers = spy(HttpHeaders.create());
        headers.add("User-Agent", List.of("Agent1", "Agent2"));
        final Request req = spy(Request.class);
        final Response res = spy(Response.class);
        when(req.headers()).thenReturn(headers);
        when(res.headers()).thenReturn(headers);

        when(configuration.getOnRequestScript()).thenReturn(
            "joined = request.headers.'User-Agent'.join('#')\n " + "request.headers.'requestResult' = joined"
        );

        when(configuration.getOnResponseScript()).thenReturn(
            "joined = response.headers.'User-Agent'.join('!')\n " + "response.headers.'responseResult' = joined"
        );

        new GroovyPolicyV3(configuration).onRequest(req, res, executionContext, policyChain);
        new GroovyPolicyV3(configuration).onResponse(req, res, executionContext, policyChain);

        assertEquals("Agent1#Agent2", req.headers().get("requestResult"));
        assertEquals("Agent1!Agent2", req.headers().get("responseResult"));
    }

    private String loadResource(String resource) throws IOException {
        InputStream stream = GroovyPolicyV3.class.getResourceAsStream(resource);
        return readInputStreamToString(stream, Charset.defaultCharset());
    }

    private String readInputStreamToString(InputStream stream, Charset defaultCharset) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(stream, defaultCharset))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                builder.append((char) c);
            }
        }

        return builder.toString();
    }
}
