package com.evoreview.github;

import com.evoreview.context.model.CapabilityReport;
import com.evoreview.context.model.ContextBuildResult;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.ReviewPlan;
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
        ReviewPlan plan = ((ContextBuildResult.Ready) result).plan();

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

        md.append("\n_Phase 2C context preview. No LLM review ran._\n");
        return md.toString();
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
