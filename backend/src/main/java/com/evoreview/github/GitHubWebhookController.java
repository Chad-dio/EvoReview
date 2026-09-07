package com.evoreview.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubProperties properties;
    private final GitHubWebhookVerifier verifier;
    private final PullRequestEventService pullRequestEventService;
    private final ObjectMapper objectMapper;

    public GitHubWebhookController(
            GitHubProperties properties,
            GitHubWebhookVerifier verifier,
            PullRequestEventService pullRequestEventService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.verifier = verifier;
        this.pullRequestEventService = pullRequestEventService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/api/github/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> webhook(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String delivery,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] payload
    ) {
        if (!properties.hasWebhookSecret()) {
            return ResponseEntity.status(503).body(Map.of("status", "github webhook is not configured"));
        }
        if (!verifier.isValid(payload, signature)) {
            log.warn("Rejected GitHub webhook delivery {} with invalid signature", delivery);
            return ResponseEntity.status(401).body(Map.of("status", "invalid signature"));
        }

        String eventName = event == null ? "" : event;
        log.info("Accepted GitHub webhook event={} delivery={}", eventName, delivery);

        try {
            if ("ping".equals(eventName)) {
                return ResponseEntity.ok(Map.of("status", "ok", "event", "ping"));
            }
            if ("pull_request".equals(eventName)) {
                JsonNode body = objectMapper.readTree(payload);
                pullRequestEventService.handle(body);
                return ResponseEntity.ok(Map.of("status", "ok", "event", "pull_request"));
            }
            return ResponseEntity.ok(Map.of("status", "ignored", "event", eventName));
        } catch (Exception ex) {
            log.error("Failed to handle GitHub webhook delivery {}", delivery, ex);
            return ResponseEntity.status(500).body(Map.of("status", "handler failed"));
        }
    }
}
