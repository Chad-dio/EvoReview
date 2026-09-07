package com.evoreview.context.model;

import java.util.List;
import java.util.Objects;

public record ReviewPlan(
        int schemaVersion,
        String contextId,
        RevisionSpec revision,
        String policyVersion,
        String configFingerprint,
        GlobalChangeSummary summary,
        List<ReviewSlice> slices,
        CapabilityReport capabilities
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ReviewPlan {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(configFingerprint, "configFingerprint");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(capabilities, "capabilities");
        slices = slices == null ? List.of() : List.copyOf(slices);
    }
}
