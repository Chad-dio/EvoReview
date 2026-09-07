package com.evoreview.github;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubWebhookVerifierTest {

    @Test
    void acceptsMatchingSignature() {
        GitHubProperties properties = new GitHubProperties();
        properties.setWebhookSecret("test-secret");
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(properties);
        byte[] payload = "{\"ok\":true}".getBytes();
        String signature = "sha256=" + GitHubWebhookVerifier.hmacSha256Hex(payload, "test-secret");
        assertTrue(verifier.isValid(payload, signature));
    }

    @Test
    void rejectsWrongSecret() {
        GitHubProperties properties = new GitHubProperties();
        properties.setWebhookSecret("test-secret");
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(properties);
        byte[] payload = "{\"ok\":true}".getBytes();
        String signature = "sha256=" + GitHubWebhookVerifier.hmacSha256Hex(payload, "other-secret");
        assertFalse(verifier.isValid(payload, signature));
    }

    @Test
    void rejectsMissingHeader() {
        GitHubProperties properties = new GitHubProperties();
        properties.setWebhookSecret("test-secret");
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(properties);
        assertFalse(verifier.isValid("{}".getBytes(), null));
    }
}
