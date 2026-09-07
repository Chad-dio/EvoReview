package com.evoreview.context.acquisition;

import com.evoreview.context.model.RevisionSpec;

import java.util.Objects;

public record PatchAcquisition(RevisionSpec revision, RawPatch patch) {

    public PatchAcquisition {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(patch, "patch");
    }
}
