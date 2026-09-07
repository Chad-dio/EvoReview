package com.evoreview.github;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class GitHubWebhookVerifier {

    private static final String SHA256_PREFIX = "sha256=";

    private final GitHubProperties properties;

    public GitHubWebhookVerifier(GitHubProperties properties) {
        this.properties = properties;
    }

    public boolean isValid(byte[] payload, String signatureHeader) {
        if (payload == null || signatureHeader == null || !properties.hasWebhookSecret()) {
            return false;
        }
        if (!signatureHeader.startsWith(SHA256_PREFIX)) {
            return false;
        }
        String expected = SHA256_PREFIX + hmacSha256Hex(payload, properties.getWebhookSecret());
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8)
        );
    }

    static String hmacSha256Hex(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to compute GitHub webhook signature", ex);
        }
    }
}
