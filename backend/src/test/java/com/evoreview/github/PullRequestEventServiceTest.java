package com.evoreview.github;

import com.evoreview.context.ContextBuilder;
import com.evoreview.context.TestSnapshots;
import com.evoreview.context.model.ContextBuildResult;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.review.ReviewEngine;
import com.evoreview.review.ReviewResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PullRequestEventServiceTest {

    private final GitHubAppClient appClient = mock(GitHubAppClient.class);
    private final ContextBuilder contextBuilder = mock(ContextBuilder.class);
    private final ReviewEngine reviewEngine = mock(ReviewEngine.class);
    private final FindingPublisher findingPublisher = mock(FindingPublisher.class);
    private final PullRequestEventService service = new PullRequestEventService(
            appClient, contextBuilder, new ReviewPlanCommentRenderer(), reviewEngine, findingPublisher);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildsContextReviewsAndPublishesFindings() throws Exception {
        ReviewPlan plan = TestSnapshots.samplePlan("ctx-1", Map.of());
        when(contextBuilder.build(5L, "o", "r", 7)).thenReturn(new ContextBuildResult.Ready(plan));
        when(reviewEngine.review(plan)).thenReturn(new ReviewResult(List.of(), true, "m", 1, 1, 0));

        service.handle(MAPPER.readTree(payload("opened")));

        verify(findingPublisher).publish(eq(5L), eq("o"), eq("r"), eq(7), eq(plan),
                any(ReviewResult.class),
                argThat(body -> body.contains("EvoReview")
                        && body.contains("heuristic-v1")
                        && body.contains("No issues found")));
        verifyNoInteractions(appClient);
    }

    @Test
    void publishesUnavailableNoteWhenReviewEngineCrashes() throws Exception {
        ReviewPlan plan = TestSnapshots.samplePlan("ctx-1", Map.of());
        when(contextBuilder.build(5L, "o", "r", 7)).thenReturn(new ContextBuildResult.Ready(plan));
        when(reviewEngine.review(plan)).thenThrow(new RuntimeException("boom"));

        service.handle(MAPPER.readTree(payload("opened")));

        verify(findingPublisher).publish(eq(5L), eq("o"), eq("r"), eq(7), eq(plan),
                argThat(result -> !result.llmAvailable()),
                argThat(body -> body.contains("LLM review unavailable")));
    }

    @Test
    void postsSkipNoteWhenContextBuildIsSkipped() throws Exception {
        when(contextBuilder.build(5L, "o", "r", 7))
                .thenReturn(new ContextBuildResult.Skipped("change too large"));

        service.handle(MAPPER.readTree(payload("opened")));

        verify(appClient).commentOnPullRequest(eq(5L), eq("o"), eq("r"), eq(7),
                argThat(body -> body.contains("skipped: change too large")));
        verifyNoInteractions(reviewEngine, findingPublisher);
    }

    @Test
    void ignoresUnrelatedActions() throws Exception {
        service.handle(MAPPER.readTree(payload("closed")));

        verifyNoInteractions(contextBuilder, appClient, reviewEngine, findingPublisher);
    }

    private static String payload(String action) {
        return """
                {"action":"%s","installation":{"id":5},
                 "repository":{"owner":{"login":"o"},"name":"r"},
                 "pull_request":{"number":7}}
                """.formatted(action);
    }
}
