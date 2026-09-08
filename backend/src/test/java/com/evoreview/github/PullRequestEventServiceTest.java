package com.evoreview.github;

import com.evoreview.context.ContextBuilder;
import com.evoreview.context.TestSnapshots;
import com.evoreview.context.model.ContextBuildResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PullRequestEventServiceTest {

    private final GitHubAppClient appClient = mock(GitHubAppClient.class);
    private final ContextBuilder contextBuilder = mock(ContextBuilder.class);
    private final PullRequestEventService service =
            new PullRequestEventService(appClient, contextBuilder, new ReviewPlanCommentRenderer());

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildsContextAndPostsRenderedComment() throws Exception {
        when(contextBuilder.build(5L, "o", "r", 7))
                .thenReturn(new ContextBuildResult.Ready(TestSnapshots.samplePlan("ctx-1", Map.of())));

        service.handle(MAPPER.readTree(payload("opened")));

        verify(appClient).commentOnPullRequest(eq(5L), eq("o"), eq("r"), eq(7),
                argThat(body -> body.contains("EvoReview")
                        && body.contains("heuristic-v1")
                        && body.contains("Phase 2C")));
    }

    @Test
    void postsSkipNoteWhenContextBuildIsSkipped() throws Exception {
        when(contextBuilder.build(5L, "o", "r", 7))
                .thenReturn(new ContextBuildResult.Skipped("change too large"));

        service.handle(MAPPER.readTree(payload("opened")));

        verify(appClient).commentOnPullRequest(eq(5L), eq("o"), eq("r"), eq(7),
                argThat(body -> body.contains("skipped: change too large")));
    }

    @Test
    void ignoresUnrelatedActions() throws Exception {
        service.handle(MAPPER.readTree(payload("closed")));

        verifyNoInteractions(contextBuilder, appClient);
    }

    private static String payload(String action) {
        return """
                {"action":"%s","installation":{"id":5},
                 "repository":{"owner":{"login":"o"},"name":"r"},
                 "pull_request":{"number":7}}
                """.formatted(action);
    }
}
