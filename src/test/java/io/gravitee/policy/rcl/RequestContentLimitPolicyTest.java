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
package io.gravitee.policy.rcl;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpHeadersValues;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.rcl.configuration.RequestContentLimitPolicyConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RequestContentLimitPolicyTest {

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private PolicyChain policyChain;

    @Mock
    private HttpHeaders httpHeaders;

    @Before
    public void init() {
        when(request.headers()).thenReturn(httpHeaders);
    }

    @Test
    public void shouldReturn_411_length_required() {
        RequestContentLimitPolicyConfiguration configuration = new RequestContentLimitPolicyConfiguration();
        RequestContentLimitPolicy policy = new RequestContentLimitPolicy(configuration);

        policy.onRequest(request, response, policyChain);

        ArgumentCaptor<PolicyResult> argument = ArgumentCaptor.forClass(PolicyResult.class);
        verify(policyChain, times(1)).failWith(argument.capture());
        Assert.assertEquals(HttpStatusCode.LENGTH_REQUIRED_411, argument.getValue().httpStatusCode());
    }

    @Test
    public void shouldReturn_400_bad_request() {
        RequestContentLimitPolicyConfiguration configuration = new RequestContentLimitPolicyConfiguration();
        RequestContentLimitPolicy policy = new RequestContentLimitPolicy(configuration);

        when(httpHeaders.getFirst(HttpHeaders.CONTENT_LENGTH)).thenReturn("invalid-content-length");

        policy.onRequest(request, response, policyChain);

        ArgumentCaptor<PolicyResult> argument = ArgumentCaptor.forClass(PolicyResult.class);
        verify(policyChain, times(1)).failWith(argument.capture());
        Assert.assertEquals(HttpStatusCode.BAD_REQUEST_400, argument.getValue().httpStatusCode());
    }

    @Test
    public void shouldContinuePolicyChain_transferEncoding() {
        RequestContentLimitPolicyConfiguration configuration = new RequestContentLimitPolicyConfiguration();
        RequestContentLimitPolicy policy = new RequestContentLimitPolicy(configuration);

        when(httpHeaders.getFirst(HttpHeaders.TRANSFER_ENCODING)).thenReturn(HttpHeadersValues.TRANSFER_ENCODING_CHUNKED);

        policy.onRequest(request, response, policyChain);

        verify(policyChain, times(1)).doNext(request, response);
    }

    @Test
    public void shouldReturn_413_entity_too_large() {
        RequestContentLimitPolicyConfiguration configuration = new RequestContentLimitPolicyConfiguration();
        configuration.setLimit(10);
        RequestContentLimitPolicy policy = new RequestContentLimitPolicy(configuration);

        when(httpHeaders.getFirst(HttpHeaders.CONTENT_LENGTH)).thenReturn("20");

        policy.onRequest(request, response, policyChain);

        ArgumentCaptor<PolicyResult> argument = ArgumentCaptor.forClass(PolicyResult.class);
        verify(policyChain, times(1)).failWith(argument.capture());
        Assert.assertEquals(HttpStatusCode.REQUEST_ENTITY_TOO_LARGE_413, argument.getValue().httpStatusCode());
    }

    @Test
    public void shouldContinuePolicyChain() {
        RequestContentLimitPolicyConfiguration configuration = new RequestContentLimitPolicyConfiguration();
        configuration.setLimit(20);
        RequestContentLimitPolicy policy = new RequestContentLimitPolicy(configuration);

        when(httpHeaders.getFirst(HttpHeaders.CONTENT_LENGTH)).thenReturn("10");

        policy.onRequest(request, response, policyChain);

        verify(policyChain, times(1)).doNext(request, response);
    }
}
