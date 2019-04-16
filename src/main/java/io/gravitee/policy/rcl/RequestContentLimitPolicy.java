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
import io.gravitee.common.util.Maps;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.stream.BufferedReadWriteStream;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.SimpleReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.rcl.configuration.RequestContentLimitPolicyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RequestContentLimitPolicy {

    /**
     * LOGGER
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(RequestContentLimitPolicy.class);

    private static final String REQUEST_CONTENT_LIMIT_TOO_LARGE = "REQUEST_CONTENT_LIMIT_TOO_LARGE";
    private static final String REQUEST_CONTENT_LIMIT_LENGTH_REQUIRED = "REQUEST_CONTENT_LIMIT_LENGTH_REQUIRED";

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
                    policyChain.failWith(
                            PolicyResult.failure(
                                    REQUEST_CONTENT_LIMIT_TOO_LARGE,
                                    HttpStatusCode.REQUEST_ENTITY_TOO_LARGE_413,
                            "The request is larger than the server is willing or able to process.",
                                    Maps.<String, Object>builder()
                                            .put("length", contentLength)
                                            .put("limit", requestContentLimitPolicyConfiguration.getLimit())
                                            .build()));
                } else {
                    policyChain.doNext(request, response);
                }
            } catch (NumberFormatException nfe) {
                policyChain.failWith(PolicyResult.failure(
                        HttpStatusCode.BAD_REQUEST_400,
                        "Content-length is not a valid integer !"));
            }
        } else if (isTransferEncoding(request)) {
            // Chunked transfer encoding, the content-length is not specified, just return the policy chain
            policyChain.doNext(request, response);
        } else {
            policyChain.failWith(
                    PolicyResult.failure(
                            REQUEST_CONTENT_LIMIT_LENGTH_REQUIRED,
                            HttpStatusCode.LENGTH_REQUIRED_411,
                            "The request did not specify the length of its content, which is required by the " +
                                    "requested resource.",
                            Maps.<String, Object>builder()
                                    .put("limit", requestContentLimitPolicyConfiguration.getLimit())
                                    .build()));
        }
    }

    @OnRequestContent
    public ReadWriteStream onRequestContent(Request request,  PolicyChain policyChain) {
        // Content stream must be used only if request contains Transfer-encoding header.
        if (isTransferEncoding(request)) {
            return new BufferedReadWriteStream() {

                private long contentLength = 0;

                @Override
                public SimpleReadWriteStream<Buffer> write(Buffer content) {
                    contentLength += content.length();

                    if (contentLength > requestContentLimitPolicyConfiguration.getLimit()) {
                        policyChain.streamFailWith(
                                PolicyResult.failure(
                                        REQUEST_CONTENT_LIMIT_TOO_LARGE,
                                        HttpStatusCode.REQUEST_ENTITY_TOO_LARGE_413,
                                        "The request is larger than the server is willing or able to process.",
                                        Maps.<String, Object>builder()
                                                .put("length", contentLength)
                                                .put("limit", requestContentLimitPolicyConfiguration.getLimit())
                                                .build()));

                        return this;
                    } else {
                        return super.write(content);
                    }
                }

                @Override
                public void end() {
                    super.end();
                }
            };
        }

        return null;
    }

    private boolean isTransferEncoding(Request request) {
        String transferEncoding = request.headers().getFirst(HttpHeaders.TRANSFER_ENCODING);
        return (transferEncoding != null && !transferEncoding.isEmpty());
    }
}
