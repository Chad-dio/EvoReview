package com.evoreview.context.acquisition;

import com.evoreview.context.model.ChangeType;

import java.util.Objects;

public record RawFilePatch(
        String path,
        String previousPath,
        ChangeType changeType,
        String patch,
        int additions,
        int deletions
) {
    public RawFilePatch {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(changeType, "changeType");
    }
}
