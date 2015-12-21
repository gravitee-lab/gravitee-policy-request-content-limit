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
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.rcl.configuration.RequestContentLimitPolicyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 */
public class RequestContentLimitPolicy {

    /**
     * LOGGER
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(RequestContentLimitPolicy.class);

    /**
     * Request content limit configuration
     */
    private final RequestContentLimitPolicyConfiguration requestContentLimitPolicyConfiguration;

    public RequestContentLimitPolicy(RequestContentLimitPolicyConfiguration requestContentLimitPolicyConfiguration) {
        this.requestContentLimitPolicyConfiguration = requestContentLimitPolicyConfiguration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, PolicyChain policyChain) {
        String contentLengthHeader = request.headers().getFirst(HttpHeaders.CONTENT_LENGTH);

        LOGGER.debug("Retrieve content-length from request: {}", contentLengthHeader);
        if (contentLengthHeader != null && ! contentLengthHeader.isEmpty()) {
            try {
                int contentLength = Integer.parseInt(contentLengthHeader);

                if (contentLength > requestContentLimitPolicyConfiguration.getLimit()) {
                    policyChain.failWith(new PolicyResult() {
                        @Override
                        public boolean isFailure() {
                            return true;
                        }

                        @Override
                        public int httpStatusCode() {
                            return HttpStatusCode.BAD_REQUEST_400;
                        }

                        @Override
                        public String message() {
                            return "Your request has been blocked because content's length is too large !";
                        }
                    });
                } else {
                    policyChain.doNext(request, response);
                }
            } catch (NumberFormatException nfe) {
                policyChain.failWith(new PolicyResult() {
                    @Override
                    public boolean isFailure() {
                        return true;
                    }

                    @Override
                    public int httpStatusCode() {
                        return HttpStatusCode.BAD_REQUEST_400;
                    }

                    @Override
                    public String message() {
                        return "Content-length is not a valid integer !";
                    }
                });
            }
        } else {
            policyChain.failWith(new PolicyResult() {
                @Override
                public boolean isFailure() {
                    return true;
                }

                @Override
                public int httpStatusCode() {
                    return HttpStatusCode.LENGTH_REQUIRED_411;
                }

                @Override
                public String message() {
                    return "The request did not specify the length of its content, which is required by the " +
                                    "requested resource.";
                }
            });
        }
    }
}
