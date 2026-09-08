package com.evoreview.review;

import com.evoreview.context.TestSnapshots;
import com.evoreview.context.model.CapabilityReport;
import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.GlobalChangeSummary;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.context.model.ReviewSlice;
import com.evoreview.context.model.RevisionSide;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewEngineTest {

    private final LlmReviewClient client = mock(LlmReviewClient.class);
    private final ReviewEngine engine = new ReviewEngine(client);

    @Test
    void aggregatesAcrossSlicesAndDedupesIdenticalFindings() {
        ReviewPlan plan = plan(List.of(
                new ReviewSlice("s1", List.of(diffItem("DIFF#1", "src/A.java", 1, 5)), List.of(), 10, 9000),
                new ReviewSlice("s2", List.of(diffItem("DIFF#2", "src/B.java", 1, 5)), List.of(), 10, 9000)));
        LlmReviewClient.LlmReviewResponse response = response(List.of(
                wire("src/A.java", 3, "warning", "T", "M")), "m", 10, 5);
        when(client.review(any())).thenReturn(response);

        ReviewResult result = engine.review(plan);

        assertEquals(1, result.findings().size());
        assertTrue(result.llmAvailable());
        assertEquals("m", result.model());
        assertEquals(20, result.promptTokens());
        assertEquals(10, result.completionTokens());
    }

    @Test
    void annotatesDiffContentAndKeepsOtherContentVerbatim() {
        ReviewPlan plan = plan(List.of(new ReviewSlice("s1", List.of(
                diffItem("DIFF#1", "src/A.java", 1, 5),
                callerItem("CALLER#1", "src/C.java")), List.of(), 10, 9000)));
        when(client.review(any())).thenReturn(response(List.of(), "m", 0, 0));

        engine.review(plan);

        ArgumentCaptor<ReviewRequest> captor = ArgumentCaptor.forClass(ReviewRequest.class);
        org.mockito.Mockito.verify(client).review(captor.capture());
        ReviewRequest sent = captor.getValue();
        assertEquals("ctx", sent.contextId());
        assertEquals("octo/repo", sent.repo());
        assertEquals(42, sent.prNumber());
        String diffContent = sent.items().get(0).content();
        assertTrue(diffContent.contains("1 |"));
        assertTrue(sent.items().get(0).required());
        assertEquals("caller code", sent.items().get(1).content());
        assertEquals("CALLER", sent.items().get(1).kind());
    }

    @Test
    void sliceFailureDegradesButKeepsOtherSlices() {
        ReviewPlan plan = plan(List.of(
                new ReviewSlice("s1", List.of(diffItem("DIFF#1", "src/A.java", 1, 5)), List.of(), 10, 9000),
                new ReviewSlice("s2", List.of(diffItem("DIFF#2", "src/B.java", 1, 5)), List.of(), 10, 9000)));
        when(client.review(any()))
                .thenReturn(response(List.of(wire("src/A.java", 3, "warning", "T", "M")), "m", 1, 1))
                .thenThrow(new LlmReviewException("boom"));

        ReviewResult result = engine.review(plan);

        assertTrue(result.llmAvailable());
        assertEquals(1, result.findings().size());
    }

    @Test
    void allSlicesFailingMarksReviewUnavailable() {
        ReviewPlan plan = plan(List.of(
                new ReviewSlice("s1", List.of(diffItem("DIFF#1", "src/A.java", 1, 5)), List.of(), 10, 9000)));
        when(client.review(any())).thenThrow(new LlmReviewException("boom"));

        ReviewResult result = engine.review(plan);

        assertFalse(result.llmAvailable());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void invalidWireFindingsAreDroppedAndCounted() {
        ReviewPlan plan = plan(List.of(
                new ReviewSlice("s1", List.of(diffItem("DIFF#1", "src/A.java", 1, 5)), List.of(), 10, 9000)));
        when(client.review(any())).thenReturn(response(List.of(
                wire("src/A.java", null, "warning", "no line", "M"),
                wire("", 3, "warning", "no path", "M"),
                wire("src/A.java", 3, "strange", "ok", "M")), "m", 1, 1));

        ReviewResult result = engine.review(plan);

        assertEquals(1, result.findings().size());
        assertEquals(2, result.droppedFindings());
        assertEquals(ReviewSeverity.WARNING, result.findings().get(0).severity());
    }

    private static LlmReviewClient.WireFinding wire(String path, Integer line, String severity, String title, String message) {
        return new LlmReviewClient.WireFinding(path, line, severity, title, message, null, List.of("DIFF#1"));
    }

    private static LlmReviewClient.LlmReviewResponse response(
            List<LlmReviewClient.WireFinding> findings, String model, int promptTokens, int completionTokens) {
        return new LlmReviewClient.LlmReviewResponse(
                findings, model, new LlmReviewClient.WireUsage(promptTokens, completionTokens), 0);
    }

    private static ContextItem diffItem(String id, String path, int start, int end) {
        return new ContextItem(id, ItemKind.DIFF_HUNK,
                ContextRef.lines(RevisionSide.HEAD, path, start, end),
                "@@ -1,1 +1,1 @@\n-x\n+y", 10, true, List.of(), List.of(), Map.of());
    }

    private static ContextItem callerItem(String id, String path) {
        return new ContextItem(id, ItemKind.CALLER,
                ContextRef.file(RevisionSide.HEAD, path),
                "caller code", 10, false, List.of(), List.of(), Map.of());
    }

    private static ReviewPlan plan(List<ReviewSlice> slices) {
        return new ReviewPlan(
                ReviewPlan.CURRENT_SCHEMA_VERSION,
                "ctx",
                TestSnapshots.sampleRevision(),
                "heuristic-v1",
                "fp",
                new GlobalChangeSummary(2, 2, 1, List.of()),
                slices,
                mock(CapabilityReport.class));
    }
}
