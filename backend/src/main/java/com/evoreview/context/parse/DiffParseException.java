package com.evoreview.context.parse;

public class DiffParseException extends RuntimeException {

    private final String path;
    private final int lineIndex;

    public DiffParseException(String path, int lineIndex, String message) {
        super("Malformed patch for " + path + " at line " + lineIndex + ": " + message);
        this.path = path;
        this.lineIndex = lineIndex;
    }

    public String path() {
        return path;
    }

    public int lineIndex() {
        return lineIndex;
    }
}
