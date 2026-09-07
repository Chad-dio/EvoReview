package com.evoreview.context.acquisition;

import java.util.List;
import java.util.Objects;

public record RawPatch(String repoId, int prNumber, List<RawFilePatch> files) {

    public RawPatch {
        Objects.requireNonNull(repoId, "repoId");
        files = files == null ? List.of() : List.copyOf(files);
    }
}
