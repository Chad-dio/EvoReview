package com.evoreview.context.model;

import java.util.List;

public record GlobalChangeSummary(int fileCount, int additions, int deletions, List<String> themes) {

    public GlobalChangeSummary {
        themes = themes == null ? List.of() : List.copyOf(themes);
    }
}
