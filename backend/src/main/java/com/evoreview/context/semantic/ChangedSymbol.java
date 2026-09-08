package com.evoreview.context.semantic;

import com.evoreview.context.model.RevisionSide;

import java.util.Objects;

public record ChangedSymbol(
        String path,
        RevisionSide side,
        String classFqn,
        String methodName,
        String signature,
        int arity,
        int startLine,
        int endLine
) {
    public ChangedSymbol {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(classFqn, "classFqn");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(signature, "signature");
    }

    public String symbolId() {
        return classFqn + "#" + signature;
    }
}
