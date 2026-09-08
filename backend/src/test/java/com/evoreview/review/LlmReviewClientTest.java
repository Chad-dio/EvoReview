package com.evoreview.review;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmReviewClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private LlmReviewClient client() {
        return new LlmReviewClient(wm.getRuntimeInfo().getHttpBaseUrl());
    }

    private static ReviewRequest request() {
        return new ReviewRequest("ctx-1", "slice-1", "o/r", 7, List.of(
                new ReviewRequestItem("DIFF#1", "DIFF_HUNK", "src/A.java", "HEAD", null, 1, 2, true, "code")));
    }

    @Test
    void postsRequestAndParsesResponse() {
        wm.stubFor(post(urlEqualTo("/review")).willReturn(okJson("""
                {"findings":[{"path":"src/A.java","line":2,"severity":"critical",
                              "title":"NPE risk","message":"x may be null","suggestion":null,
                              "citedItemIds":["DIFF#1"]}],
                 "model":"test-model","usage":{"promptTokens":11,"completionTokens":7},
                 "droppedFindings":1}
                """)));

        LlmReviewClient.LlmReviewResponse response = client().review(request());

        assertEquals(1, response.findings().size());
        assertEquals("src/A.java", response.findings().get(0).path());
        assertEquals("critical", response.findings().get(0).severity());
        assertEquals("test-model", response.model());
        assertEquals(11, response.usage().promptTokens());
        assertEquals(1, response.droppedFindings());
        wm.verify(postRequestedFor(urlEqualTo("/review"))
                .withRequestBody(containing("\"itemId\":\"DIFF#1\""))
                .withRequestBody(containing("\"sliceId\":\"slice-1\"")));
    }

    @Test
    void non200ResponseRaises() {
        wm.stubFor(post(urlEqualTo("/review")).willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.status(503)));

        assertThrows(LlmReviewException.class, () -> client().review(request()));
    }

    @Test
    void unknownResponseFieldsAreIgnored() {
        wm.stubFor(post(urlEqualTo("/review")).willReturn(okJson("""
                {"findings":[],"model":"m","futureField":{"x":1}}
                """)));

        LlmReviewClient.LlmReviewResponse response = client().review(request());

        assertEquals("m", response.model());
        assertEquals(0, response.findings().size());
    }
}
