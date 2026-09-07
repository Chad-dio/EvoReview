package com.evoreview.context.model;

import java.util.Map;
import java.util.Objects;

/**
 * Canonical, byte-reproducible persisted form of a build. Operational metadata
 * (timestamps, latency, cache hits, node identity) belongs to
 * {@link ContextBuildTelemetry} and must never be added here.
 */
public record ContextSnapshot(ReviewPlan plan, Map<String, String> diagnostics) {

    public ContextSnapshot {
        Objects.requireNonNull(plan, "plan");
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }
}
