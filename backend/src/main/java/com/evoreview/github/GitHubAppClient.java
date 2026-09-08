package com.evoreview.github;

import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.authorization.JWTTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

@Component
public class GitHubAppClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubAppClient.class);

    private final GitHubProperties properties;

    public GitHubAppClient(GitHubProperties properties) {
        this.properties = properties;
    }

    public void commentOnPullRequest(
            long installationId,
            String owner,
            String repo,
            int pullNumber,
            String body
    ) throws IOException {
        GitHub installation = loginAsInstallation(installationId);
        installation.getRepository(owner + "/" + repo).getPullRequest(pullNumber).comment(body);
        log.info("Posted review comment on {}/{}#{}", owner, repo, pullNumber);
    }

    public GitHub loginAsInstallation(long installationId) throws IOException {
        return new GitHubBuilder()
                .withAppInstallationToken(createInstallationToken(installationId))
                .build();
    }

    public String createInstallationToken(long installationId) throws IOException {
        Path privateKey = properties.resolvedPrivateKeyPath();
        try {
            String pem = GitHubPrivateKeyPem.toPkcs8Pem(Files.readString(privateKey));
            GitHub appGitHub = new GitHubBuilder()
                    .withAuthorizationProvider(new JWTTokenProvider(properties.getAppId(), pem))
                    .build();
            GHAppInstallation installation = appGitHub.getApp().getInstallationById(installationId);
            GHAppInstallationToken token = installation.createToken().create();
            return token.getToken();
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IOException("Unable to load GitHub App private key", ex);
        }
    }
}
