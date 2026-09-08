package com.evoreview.context.semantic;

import java.util.Objects;

public record MethodDecl(String name, String signature, int arity, int startLine, int endLine) {

    public MethodDecl {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(signature, "signature");
    }

    public boolean containsLine(int line) {
        return startLine <= line && line <= endLine;
    }
}
