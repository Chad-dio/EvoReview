package com.evoreview.context.model;

import java.util.Objects;

public record ContextEdge(
        EdgeType type,
        ContextRef from,
        ContextRef to,
        double confidence,
        RecallSource source
) {
    public ContextEdge {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(source, "source");
    }
}
