package com.evoreview.context.model;

import java.util.List;

public record Hunk(int oldStart, int oldLines, int newStart, int newLines, List<HunkLine> lines) {

    public Hunk {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
