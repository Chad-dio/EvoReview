package com.evoreview.context.semantic;

import java.util.Objects;

public record SourceInput(String path, String content) {

    public SourceInput {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
    }
}
