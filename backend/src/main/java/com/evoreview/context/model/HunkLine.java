package com.evoreview.context.model;

import java.util.Objects;

public record HunkLine(LineType type, Integer oldLineNo, Integer newLineNo, String content) {

    public HunkLine {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(content, "content");
    }

    public static HunkLine context(int oldLineNo, int newLineNo, String content) {
        return new HunkLine(LineType.CONTEXT, oldLineNo, newLineNo, content);
    }

    public static HunkLine added(int newLineNo, String content) {
        return new HunkLine(LineType.ADDED, null, newLineNo, content);
    }

    public static HunkLine removed(int oldLineNo, String content) {
        return new HunkLine(LineType.REMOVED, oldLineNo, null, content);
    }
}
