package com.evoreview.context.semantic;

import java.util.Objects;

public record ParseFailure(String path, String reason) {

    public ParseFailure {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(reason, "reason");
    }
}
