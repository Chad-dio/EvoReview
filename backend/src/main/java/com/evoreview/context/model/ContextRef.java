package com.evoreview.context.model;

import java.util.Objects;

public record ContextRef(
        RevisionSide revision,
        String path,
        Integer startLine,
        Integer endLine,
        String symbolId,
        String contentSha
) {
    public ContextRef {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(path, "path");
    }

    public static ContextRef file(RevisionSide revision, String path) {
        return new ContextRef(revision, path, null, null, null, null);
    }

    public static ContextRef lines(RevisionSide revision, String path, int startLine, int endLine) {
        return new ContextRef(revision, path, startLine, endLine, null, null);
    }
}
