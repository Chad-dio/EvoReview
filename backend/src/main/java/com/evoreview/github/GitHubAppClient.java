package com.evoreview.github;

import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
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
import java.util.ArrayList;
import java.util.List;

@Component
public class GitHubAppClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubAppClient.class);
    private static final int MAX_FILES_IN_COMMENT = 20;

    private final GitHubProperties properties;

    public GitHubAppClient(GitHubProperties properties) {
        this.properties = properties;
    }

    public void commentOnPullRequest(
            long installationId,
            String owner,
            String repo,
            int pullNumber,
            String action
    ) throws IOException {
        GitHub installation = loginAsInstallation(installationId);
        GHPullRequest pullRequest = installation.getRepository(owner + "/" + repo).getPullRequest(pullNumber);
        List<String> fileLines = new ArrayList<>();
        int fileCount = 0;
        for (GHPullRequestFileDetail file : pullRequest.listFiles()) {
            fileCount++;
            if (fileLines.size() < MAX_FILES_IN_COMMENT) {
                fileLines.add(String.format(
                        "- `%s` (%s, +%d / -%d)",
                        file.getFilename(),
                        file.getStatus(),
                        file.getAdditions(),
                        file.getDeletions()
                ));
            }
        }
        StringBuilder body = new StringBuilder();
        body.append("## EvoReview\n\n");
        body.append("Received pull request `").append(action).append("`.\n\n");
        body.append("Head: `").append(pullRequest.getHead().getSha()).append("`\n\n");
        body.append("Changed files: **").append(fileCount).append("**\n\n");
        for (String line : fileLines) {
            body.append(line).append('\n');
        }
        if (fileCount > MAX_FILES_IN_COMMENT) {
            body.append("\n… and ").append(fileCount - MAX_FILES_IN_COMMENT).append(" more files.\n");
        }
        body.append("\nThis is a Phase 1 placeholder comment. No LLM review ran.\n");
        pullRequest.comment(body.toString());
        log.info("Posted placeholder review comment on {}/{}#{}", owner, repo, pullNumber);
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
