package com.evoreview.context.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Operational metadata for one context build. Logged and metered only;
 * never part of the canonical {@link ContextSnapshot}.
 */
public record ContextBuildTelemetry(
        String contextId,
        String deliveryId,
        Instant builtAt,
        long durationMs,
        double cacheHitRate,
        String node
) {
    public ContextBuildTelemetry {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(builtAt, "builtAt");
    }
}
