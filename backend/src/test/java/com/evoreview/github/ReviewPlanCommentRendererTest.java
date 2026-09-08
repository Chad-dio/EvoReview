package com.evoreview.github;

import com.evoreview.context.TestSnapshots;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.review.ReviewFinding;
import com.evoreview.review.ReviewResult;
import com.evoreview.review.ReviewSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewPlanCommentRendererTest {

    private final ReviewPlanCommentRenderer renderer = new ReviewPlanCommentRenderer();
    private final ReviewPlan plan = TestSnapshots.samplePlan("ctx-1", Map.of());

    @Test
    void reviewSectionSummarizesFindingsAndModel() {
        ReviewResult result = new ReviewResult(List.of(
                new ReviewFinding("src/A.java", 3, ReviewSeverity.CRITICAL, "NPE", "m", null, List.of()),
                new ReviewFinding("src/A.java", 4, ReviewSeverity.WARNING, "Style", "m", null, List.of())),
                true, "test-model", 12, 4, 1);

        String body = renderer.renderReview(plan, result);

        assertTrue(body.contains("test-model"));
        assertTrue(body.contains("critical=1"));
        assertTrue(body.contains("warning=1"));
        assertTrue(body.contains("dropped malformed findings: 1"));
        assertFalse(body.contains("No LLM review ran"));
    }

    @Test
    void unavailableReviewIsCalledOut() {
        String body = renderer.renderReview(plan, ReviewResult.unavailable());

        assertTrue(body.contains("LLM review unavailable"));
    }

    @Test
    void cleanReviewSaysNoIssuesFound() {
        String body = renderer.renderReview(plan, new ReviewResult(List.of(), true, "m", 1, 1, 0));

        assertTrue(body.contains("No issues found"));
    }
}
