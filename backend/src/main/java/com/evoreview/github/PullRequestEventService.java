package com.evoreview.github;

import com.evoreview.context.ContextBuilder;
import com.evoreview.context.model.ContextBuildResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Set;

@Service
public class PullRequestEventService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestEventService.class);
    private static final Set<String> HANDLED_ACTIONS = Set.of("opened", "reopened", "synchronize");

    private final GitHubAppClient gitHubAppClient;
    private final ContextBuilder contextBuilder;
    private final ReviewPlanCommentRenderer commentRenderer;

    public PullRequestEventService(
            GitHubAppClient gitHubAppClient,
            ContextBuilder contextBuilder,
            ReviewPlanCommentRenderer commentRenderer
    ) {
        this.gitHubAppClient = gitHubAppClient;
        this.contextBuilder = contextBuilder;
        this.commentRenderer = commentRenderer;
    }

    public void handle(JsonNode payload) throws IOException {
        String action = payload.path("action").asText();
        if (!HANDLED_ACTIONS.contains(action)) {
            log.info("Ignoring pull_request action {}", action);
            return;
        }

        long installationId = payload.path("installation").path("id").asLong();
        String owner = payload.path("repository").path("owner").path("login").asText();
        String repo = payload.path("repository").path("name").asText();
        int number = payload.path("pull_request").path("number").asInt();
        if (installationId == 0 || owner.isBlank() || repo.isBlank() || number == 0) {
            throw new IOException("pull_request payload is missing repository or installation fields");
        }

        log.info("Handling pull_request {} on {}/{}#{}", action, owner, repo, number);
        ContextBuildResult result = contextBuilder.build(installationId, owner, repo, number);
        gitHubAppClient.commentOnPullRequest(
                installationId, owner, repo, number, commentRenderer.render(result));
    }
}
