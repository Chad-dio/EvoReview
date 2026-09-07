package com.evoreview.github;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitHubWebhookController.class)
@EnableConfigurationProperties(GitHubProperties.class)
@Import(GitHubWebhookVerifier.class)
@TestPropertySource(properties = {
        "evoreview.github.app-id=1",
        "evoreview.github.webhook-secret=test-secret",
        "evoreview.github.private-key-path=unused.pem"
})
class GitHubWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PullRequestEventService pullRequestEventService;

    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean("gitHubEventExecutor")
        Executor gitHubEventExecutor() {
            return Runnable::run;
        }
    }

    @Test
    void pingWithValidSignatureReturnsOk() throws Exception {
        byte[] payload = "{\"zen\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "ping")
                        .header("X-GitHub-Delivery", "delivery-1")
                        .header("X-Hub-Signature-256", signature(payload))
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event").value("ping"))
                .andExpect(jsonPath("$.status").value("ok"));
        verify(pullRequestEventService, never()).handle(any());
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        byte[] payload = "{\"zen\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "ping")
                        .header("X-Hub-Signature-256", "sha256=deadbeef")
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pullRequestIsAcceptedAndDispatched() throws Exception {
        byte[] payload = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-2")
                        .header("X-Hub-Signature-256", signature(payload))
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.event").value("pull_request"))
                .andExpect(jsonPath("$.status").value("accepted"));
        verify(pullRequestEventService, times(1)).handle(any());
    }

    @Test
    void pullRequestHandlerFailureStillReturnsAccepted() throws Exception {
        doThrow(new IOException("boom")).when(pullRequestEventService).handle(any());
        byte[] payload = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-3")
                        .header("X-Hub-Signature-256", signature(payload))
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void malformedPullRequestPayloadReturnsBadRequest() throws Exception {
        byte[] payload = "not-json".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-4")
                        .header("X-Hub-Signature-256", signature(payload))
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("invalid payload"));
        verify(pullRequestEventService, never()).handle(any());
    }

    private static String signature(byte[] payload) {
        return "sha256=" + GitHubWebhookVerifier.hmacSha256Hex(payload, "test-secret");
    }
}
