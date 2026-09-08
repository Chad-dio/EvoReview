package com.evoreview.github;

import com.evoreview.context.model.CapabilityReport;
import com.evoreview.context.model.ContextBuildResult;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.review.ReviewResult;
import com.evoreview.review.ReviewSeverity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Presentation of a context build as a PR comment. Lives in the github package
 * on purpose: the context layer returns data, the caller decides what to say.
 */
@Component
public class ReviewPlanCommentRenderer {

    private static final int MAX_METHODS_LISTED = 10;

    public String render(ContextBuildResult result) {
        if (result instanceof ContextBuildResult.Skipped skipped) {
            return "## EvoReview\n\nContext build skipped: " + skipped.reason() + "\n";
        }
        return renderPlanBody(((ContextBuildResult.Ready) result).plan())
                + "\n_Phase 2C context preview. No LLM review ran._\n";
    }

    public String renderReview(ReviewPlan plan, ReviewResult review) {
        StringBuilder md = new StringBuilder(renderPlanBody(plan));
        md.append("\n### LLM Review\n");
        if (!review.llmAvailable()) {
            md.append("LLM review unavailable (service down or not configured); context summary only.\n");
            return md.toString();
        }
        md.append("Model `").append(review.model()).append("` · tokens: prompt=")
                .append(review.promptTokens()).append(", completion=").append(review.completionTokens());
        if (review.droppedFindings() > 0) {
            md.append(" · dropped malformed findings: ").append(review.droppedFindings());
        }
        md.append('\n');
        if (review.findings().isEmpty()) {
            md.append("No issues found.\n");
        } else {
            long critical = countSeverity(review, ReviewSeverity.CRITICAL);
            long warning = countSeverity(review, ReviewSeverity.WARNING);
            long suggestion = countSeverity(review, ReviewSeverity.SUGGESTION);
            md.append("**").append(review.findings().size()).append("** findings (critical=")
                    .append(critical).append(", warning=").append(warning)
                    .append(", suggestion=").append(suggestion).append(")\n");
        }
        return md.toString();
    }

    private String renderPlanBody(ReviewPlan plan) {
        StringBuilder md = new StringBuilder("## EvoReview\n\n");
        md.append("Context built for `").append(shortSha(plan.revision().headSha()))
                .append("` · policy `").append(plan.policyVersion()).append("` · slices: **")
                .append(plan.slices().size()).append("**\n\n");
        md.append("Changed files: **").append(plan.summary().fileCount())
                .append("** (+").append(plan.summary().additions())
                .append(" / -").append(plan.summary().deletions()).append(")\n\n");

        List<String> methods = plan.slices().stream()
                .flatMap(slice -> slice.included().stream())
                .filter(item -> item.kind() == ItemKind.CHANGED_METHOD)
                .map(item -> item.ref().symbolId())
                .distinct()
                .sorted()
                .limit(MAX_METHODS_LISTED + 1)
                .toList();
        if (!methods.isEmpty()) {
            md.append("Changed methods:\n");
            methods.stream().limit(MAX_METHODS_LISTED)
                    .forEach(method -> md.append("- `").append(method).append("`\n"));
            if (methods.size() > MAX_METHODS_LISTED) {
                md.append("- …\n");
            }
            md.append('\n');
        }

        long callers = countByKind(plan, ItemKind.CALLER);
        long overrides = countByKind(plan, ItemKind.OVERRIDE);
        long tests = countByKind(plan, ItemKind.TEST);
        md.append("Related context: **").append(callers).append("** callers, **")
                .append(overrides).append("** overrides, **").append(tests)
                .append("** test references\n");

        CapabilityReport capabilities = plan.capabilities();
        md.append("\nCapabilities: patch=").append(capabilities.patch())
                .append(", snapshot=").append(capabilities.headSnapshot())
                .append(", mergeBase=").append(capabilities.mergeBaseFiles())
                .append(", astCoverage=").append(Math.round(capabilities.astCoverage() * 100)).append("%\n");
        return md.toString();
    }

    private static long countSeverity(ReviewResult review, ReviewSeverity severity) {
        return review.findings().stream().filter(f -> f.severity() == severity).count();
    }

    private static long countByKind(ReviewPlan plan, ItemKind kind) {
        return plan.slices().stream()
                .flatMap(slice -> slice.included().stream())
                .filter(item -> item.kind() == kind)
                .count();
    }

    private static String shortSha(String sha) {
        return sha.length() <= 7 ? sha : sha.substring(0, 7);
    }
}
