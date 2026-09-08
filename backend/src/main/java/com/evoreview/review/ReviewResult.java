package com.evoreview.review;

import java.util.List;

public record ReviewResult(
        List<ReviewFinding> findings,
        boolean llmAvailable,
        String model,
        int promptTokens,
        int completionTokens,
        int droppedFindings) {

    public ReviewResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public static ReviewResult unavailable() {
        return new ReviewResult(List.of(), false, "", 0, 0, 0);
    }
}
