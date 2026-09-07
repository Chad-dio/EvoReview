package com.evoreview.context.model;

import java.util.Objects;

public sealed interface ContextBuildResult {

    record Ready(ReviewPlan plan) implements ContextBuildResult {
        public Ready {
            Objects.requireNonNull(plan, "plan");
        }
    }

    record Skipped(String reason) implements ContextBuildResult {
        public Skipped {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
