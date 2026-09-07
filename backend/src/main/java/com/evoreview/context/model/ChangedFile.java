package com.evoreview.context.model;

import java.util.List;
import java.util.Objects;

public record ChangedFile(
        String path,
        String previousPath,
        ChangeType changeType,
        FileCategory category,
        List<Hunk> hunks
) {
    public ChangedFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(changeType, "changeType");
        Objects.requireNonNull(category, "category");
        hunks = hunks == null ? List.of() : List.copyOf(hunks);
    }
}
