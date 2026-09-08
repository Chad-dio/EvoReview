package com.evoreview.review;

import java.util.Locale;

public enum ReviewSeverity {
    CRITICAL,
    WARNING,
    SUGGESTION;

    public static ReviewSeverity fromWire(String value) {
        if (value != null) {
            try {
                return ReviewSeverity.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // unknown severity from the model; clamp below
            }
        }
        return WARNING;
    }
}
