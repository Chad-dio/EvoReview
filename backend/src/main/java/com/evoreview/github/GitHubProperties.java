package com.evoreview.github;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "evoreview.github")
public class GitHubProperties {

    private String appId = "";
    private String clientId = "";
    private String webhookSecret = "";
    private String privateKeyPath = ".local/github-app.pem";
    private String publicBaseUrl = "";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId == null ? "" : appId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId == null ? "" : clientId;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath == null ? "" : privateKeyPath;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl == null ? "" : trimTrailingSlash(publicBaseUrl);
    }

    public String webhookUrl() {
        if (publicBaseUrl.isBlank()) {
            return "";
        }
        return publicBaseUrl + "/api/github/webhook";
    }

    public Path resolvedPrivateKeyPath() {
        Path path = Path.of(privateKeyPath);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path;
    }

    public boolean isConfigured() {
        return !appId.isBlank()
                && !webhookSecret.isBlank()
                && Files.isRegularFile(resolvedPrivateKeyPath());
    }

    public boolean hasWebhookSecret() {
        return !webhookSecret.isBlank();
    }

    private static String trimTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
