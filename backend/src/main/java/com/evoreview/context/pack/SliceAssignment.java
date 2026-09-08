package com.evoreview.context.pack;

import java.util.List;
import java.util.Objects;

public record SliceAssignment(String sliceId, List<String> changedPaths) {

    public SliceAssignment {
        Objects.requireNonNull(sliceId, "sliceId");
        changedPaths = changedPaths == null ? List.of() : List.copyOf(changedPaths);
    }
}
