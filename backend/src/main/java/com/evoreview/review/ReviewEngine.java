package com.evoreview.review;

import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.context.model.ReviewSlice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class ReviewEngine {

    private static final Logger log = LoggerFactory.getLogger(ReviewEngine.class);

    private final LlmReviewClient llmClient;

    public ReviewEngine(LlmReviewClient llmClient) {
        this.llmClient = llmClient;
    }

    public ReviewResult review(ReviewPlan plan) {
        List<ReviewFinding> findings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String model = "";
        int promptTokens = 0;
        int completionTokens = 0;
        int dropped = 0;
        int failures = 0;

        for (ReviewSlice slice : plan.slices()) {
            ReviewRequest request = buildRequest(plan, slice);
            try {
                LlmReviewClient.LlmReviewResponse response = llmClient.review(request);
                if (response.model() != null && !response.model().isBlank()) {
                    model = response.model();
                }
                if (response.usage() != null) {
                    promptTokens += response.usage().promptTokens() == null ? 0 : response.usage().promptTokens();
                    completionTokens += response.usage().completionTokens() == null ? 0 : response.usage().completionTokens();
                }
                dropped += response.droppedFindings() == null ? 0 : response.droppedFindings();
                List<LlmReviewClient.WireFinding> wireFindings =
                        response.findings() == null ? List.of() : response.findings();
                for (LlmReviewClient.WireFinding wire : wireFindings) {
                    ReviewFinding finding = toFinding(wire);
                    if (finding == null) {
                        dropped++;
                        continue;
                    }
                    String key = finding.path() + "|" + finding.line() + "|" + finding.title();
                    if (seen.add(key)) {
                        findings.add(finding);
                    }
                }
            } catch (RuntimeException e) {
                failures++;
                log.warn("LLM review failed for slice {} of {}: {}", slice.sliceId(), plan.contextId(), e.getMessage());
            }
        }

        boolean available = plan.slices().isEmpty() || failures < plan.slices().size();
        return new ReviewResult(List.copyOf(findings), available, model, promptTokens, completionTokens, dropped);
    }

    private ReviewRequest buildRequest(ReviewPlan plan, ReviewSlice slice) {
        List<ReviewRequestItem> items = new ArrayList<>();
        for (ContextItem item : slice.included()) {
            ContextRef ref = item.ref();
            String content = item.content();
            if (item.kind() == ItemKind.DIFF_HUNK) {
                content = DiffLineAnnotator.annotate(content);
            }
            items.add(new ReviewRequestItem(
                    item.itemId(),
                    item.kind().name(),
                    ref.path(),
                    ref.revision() == null ? null : ref.revision().name(),
                    ref.symbolId(),
                    ref.startLine(),
                    ref.endLine(),
                    item.required(),
                    content));
        }
        return new ReviewRequest(
                plan.contextId(),
                slice.sliceId(),
                plan.revision().repoId(),
                plan.revision().prNumber(),
                List.copyOf(items));
    }

    private ReviewFinding toFinding(LlmReviewClient.WireFinding wire) {
        if (wire.path() == null || wire.path().isBlank()
                || wire.title() == null || wire.title().isBlank()
                || wire.message() == null || wire.message().isBlank()
                || wire.line() == null || wire.line() <= 0) {
            return null;
        }
        String suggestion = wire.suggestion() == null || wire.suggestion().isBlank() ? null : wire.suggestion();
        return new ReviewFinding(
                wire.path(),
                wire.line(),
                ReviewSeverity.fromWire(wire.severity()),
                wire.title(),
                wire.message(),
                suggestion,
                wire.citedItemIds());
    }
}
