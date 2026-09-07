package com.evoreview.context.model;

import java.util.Objects;

public record RevisionSpec(
        String repoId,
        int prNumber,
        String baseSha,
        String mergeBaseSha,
        String headSha,
        String patchSha
) {
    public RevisionSpec {
        Objects.requireNonNull(repoId, "repoId");
        Objects.requireNonNull(baseSha, "baseSha");
        Objects.requireNonNull(mergeBaseSha, "mergeBaseSha");
        Objects.requireNonNull(headSha, "headSha");
        Objects.requireNonNull(patchSha, "patchSha");
        if (prNumber <= 0) {
            throw new IllegalArgumentException("prNumber must be positive");
        }
    }
}
