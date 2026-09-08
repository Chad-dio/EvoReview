package com.evoreview.context.semantic;

import java.util.Objects;

/**
 * One method call site. receiverName is the raw scope text ("fooService",
 * "this.fooService", "" for implicit this) — disambiguation happens in recall.
 */
public record InvocationSite(
        String path,
        int line,
        String receiverName,
        String enclosingTypeFqn,
        String enclosingMethod
) {
    public InvocationSite {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(receiverName, "receiverName");
    }
}
