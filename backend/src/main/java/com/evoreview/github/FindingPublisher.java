package com.evoreview.github;

import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.context.model.ReviewSlice;
import com.evoreview.review.ReviewFinding;
import com.evoreview.review.ReviewResult;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewBuilder;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes review findings to GitHub. Findings whose (path, line) falls inside an
 * included diff hunk are posted as inline review comments; anything else is folded
 * into the review body. If the inline review fails (e.g. GitHub rejects a line),
 * degrades to a single plain comment so signal is never lost.
 */
@Component
public class FindingPublisher {

    private static final Logger log = LoggerFactory.getLogger(FindingPublisher.class);

    private final GitHubAppClient appClient;

    public FindingPublisher(GitHubAppClient appClient) {
        this.appClient = appClient;
    }

    public void publish(long installationId, String owner, String repo, int number,
                        ReviewPlan plan, ReviewResult review, String summaryBody) {
        Map<String, List<int[]>> anchors = anchorRanges(plan);
        List<ReviewFinding> anchored = new ArrayList<>();
        List<ReviewFinding> unanchored = new ArrayList<>();
        for (ReviewFinding finding : review.findings()) {
            (isAnchored(anchors, finding) ? anchored : unanchored).add(finding);
        }

        String body = summaryBody
                + renderFindingList(unanchored, "Findings that could not be anchored to a changed line");

        if (!anchored.isEmpty()) {
            try {
                publishInlineReview(installationId, owner, repo, number, anchored, body);
                return;
            } catch (Exception e) {
                log.warn("Inline review failed for {}/{}#{}: {}; falling back to a plain comment",
                        owner, repo, number, e.getMessage());
            }
            body += renderFindingList(anchored, "Findings (inline posting failed)");
        }
        try {
            appClient.commentOnPullRequest(installationId, owner, repo, number, body);
        } catch (IOException e) {
            log.error("Failed to post review comment on {}/{}#{}: {}", owner, repo, number, e.getMessage());
        }
    }

    private void publishInlineReview(long installationId, String owner, String repo, int number,
                                     List<ReviewFinding> findings, String body) throws IOException {
        GitHub github = appClient.loginAsInstallation(installationId);
        GHPullRequest pr = github.getRepository(owner + "/" + repo).getPullRequest(number);
        GHPullRequestReviewBuilder builder = pr.createReview().body(body);
        for (ReviewFinding finding : findings) {
            builder = builder.singleLineComment(renderInlineComment(finding), finding.path(), finding.line());
        }
        builder.create();
        log.info("Published inline review with {} finding(s) on {}/{}#{}", findings.size(), owner, repo, number);
    }

    private static Map<String, List<int[]>> anchorRanges(ReviewPlan plan) {
        Map<String, List<int[]>> ranges = new LinkedHashMap<>();
        for (ReviewSlice slice : plan.slices()) {
            for (ContextItem item : slice.included()) {
                if (item.kind() != ItemKind.DIFF_HUNK) {
                    continue;
                }
                if (item.ref().startLine() == null || item.ref().endLine() == null) {
                    continue;
                }
                ranges.computeIfAbsent(item.ref().path(), k -> new ArrayList<>())
                        .add(new int[]{item.ref().startLine(), item.ref().endLine()});
            }
        }
        return ranges;
    }

    private static boolean isAnchored(Map<String, List<int[]>> anchors, ReviewFinding finding) {
        List<int[]> ranges = anchors.get(finding.path());
        if (ranges == null) {
            return false;
        }
        for (int[] range : ranges) {
            if (finding.line() >= range[0] && finding.line() <= range[1]) {
                return true;
            }
        }
        return false;
    }

    private static String renderInlineComment(ReviewFinding finding) {
        StringBuilder sb = new StringBuilder();
        sb.append("**[").append(finding.severity()).append("] ").append(finding.title()).append("**\n\n");
        sb.append(finding.message());
        if (finding.suggestion() != null) {
            sb.append("\n\nSuggestion: ").append(finding.suggestion());
        }
        if (!finding.citedItemIds().isEmpty()) {
            sb.append("\n\n_Cited context: ").append(String.join(", ", finding.citedItemIds())).append("_");
        }
        return sb.toString();
    }

    private static String renderFindingList(List<ReviewFinding> findings, String heading) {
        if (findings.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n**").append(heading).append(":**\n");
        for (ReviewFinding finding : findings) {
            sb.append("- `").append(finding.path()).append(':').append(finding.line())
                    .append("` [").append(finding.severity()).append("] **")
                    .append(finding.title()).append("** — ").append(finding.message()).append('\n');
        }
        return sb.toString();
    }
}
