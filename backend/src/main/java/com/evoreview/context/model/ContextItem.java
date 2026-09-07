package com.evoreview.context.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ContextItem(
        String itemId,
        ItemKind kind,
        ContextRef ref,
        String content,
        int estimatedTokens,
        boolean required,
        List<String> requires,
        List<RecallSource> sources,
        Map<String, Double> features
) {
    public ContextItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(content, "content");
        requires = requires == null ? List.of() : List.copyOf(requires);
        sources = sources == null ? List.of() : List.copyOf(sources);
        features = features == null ? Map.of() : Map.copyOf(features);
    }
}
