package com.evoreview.context.model;

import java.util.List;
import java.util.Objects;

public record ReviewSlice(
        String sliceId,
        List<ContextItem> included,
        List<DroppedItem> dropped,
        int estimatedTokens,
        int budget
) {
    public ReviewSlice {
        Objects.requireNonNull(sliceId, "sliceId");
        included = included == null ? List.of() : List.copyOf(included);
        dropped = dropped == null ? List.of() : List.copyOf(dropped);
    }
}
