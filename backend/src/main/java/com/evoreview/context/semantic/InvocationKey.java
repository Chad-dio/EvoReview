package com.evoreview.context.semantic;

import java.util.Objects;

public record InvocationKey(String methodName, int arity) {

    public InvocationKey {
        Objects.requireNonNull(methodName, "methodName");
    }
}
