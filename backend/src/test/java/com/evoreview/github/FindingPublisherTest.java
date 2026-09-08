package com.evoreview.github;

import com.evoreview.context.TestSnapshots;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.review.ReviewFinding;
import com.evoreview.review.ReviewResult;
import com.evoreview.review.ReviewSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindingPublisherTest {

    private final GitHubAppClient appClient = mock(GitHubAppClient.class);
    private final GitHub github = mock(GitHub.class);
    private final GHRepository ghRepository = mock(GHRepository.class);
    private final GHPullRequest pr = mock(GHPullRequest.class);
    private final GHPullRequestReviewBuilder reviewBuilder = mock(GHPullRequestReviewBuilder.class);
    private final FindingPublisher publisher = new FindingPublisher(appClient);

    private final ReviewPlan plan = TestSnapshots.samplePlan("ctx-1", Map.of());

    @BeforeEach
    void wireGitHub() throws IOException {
        when(appClient.loginAsInstallation(5L)).thenReturn(github);
        when(github.getRepository("o/r")).thenReturn(ghRepository);
        when(ghRepository.getPullRequest(7)).thenReturn(pr);
        when(pr.createReview()).thenReturn(reviewBuilder);
        when(reviewBuilder.body(anyString())).thenReturn(reviewBuilder);
        when(reviewBuilder.singleLineComment(anyString(), anyString(), anyInt())).thenReturn(reviewBuilder);
    }

    @Test
    void postsAnchoredFindingsInlineAndFoldsUnanchoredIntoBody() throws Exception {
        ReviewResult result = result(List.of(
                finding("src/A.java", 3, "Anchored"),   // inside diff hunk lines 1-5
                finding("src/B.java", 9, "Unanchored")));

        publisher.publish(5L, "o", "r", 7, plan, result, "SUMMARY");

        verify(reviewBuilder).body(argThat(body ->
                body.contains("SUMMARY") && body.contains("src/B.java") && body.contains("Unanchored")));
        verify(reviewBuilder).singleLineComment(argThat(c -> c.contains("Anchored")), eq("src/A.java"), eq(3));
        verify(reviewBuilder).create();
        verify(appClient, never()).commentOnPullRequest(anyLong(), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void fallsBackToPlainCommentWhenInlineReviewFails() throws Exception {
        when(reviewBuilder.create()).thenThrow(new IOException("422 Validation Failed"));
        ReviewResult result = result(List.of(finding("src/A.java", 3, "Anchored")));

        publisher.publish(5L, "o", "r", 7, plan, result, "SUMMARY");

        verify(appClient).commentOnPullRequest(eq(5L), eq("o"), eq("r"), eq(7), argThat(body ->
                body.contains("SUMMARY") && body.contains("Anchored") && body.contains("inline posting failed")));
    }

    @Test
    void postsPlainCommentWhenNothingCanBeAnchored() throws IOException {
        ReviewResult result = result(List.of(finding("src/B.java", 9, "Unanchored")));

        publisher.publish(5L, "o", "r", 7, plan, result, "SUMMARY");

        verify(pr, never()).createReview();
        verify(appClient).commentOnPullRequest(eq(5L), eq("o"), eq("r"), eq(7), argThat(body ->
                body.contains("Unanchored") && !body.contains("inline posting failed")));
    }

    @Test
    void postsPlainCommentWhenThereAreNoFindings() throws IOException {
        publisher.publish(5L, "o", "r", 7, plan, result(List.of()), "SUMMARY");

        verify(appClient).commentOnPullRequest(eq(5L), eq("o"), eq("r"), eq(7), eq("SUMMARY"));
    }

    private static ReviewFinding finding(String path, int line, String title) {
        return new ReviewFinding(path, line, ReviewSeverity.WARNING, title, "msg", null, List.of("DIFF#1"));
    }

    private static ReviewResult result(List<ReviewFinding> findings) {
        return new ReviewResult(findings, true, "m", 1, 1, 0);
    }
}
