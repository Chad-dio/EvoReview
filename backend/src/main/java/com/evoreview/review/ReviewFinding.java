package com.evoreview.review;

import java.util.List;

public record ReviewFinding(
        String path,
        int line,
        ReviewSeverity severity,
        String title,
        String message,
        String suggestion,
        List<String> citedItemIds) {

    public ReviewFinding {
        citedItemIds = citedItemIds == null ? List.of() : List.copyOf(citedItemIds);
    }
}
