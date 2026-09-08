package com.evoreview.context.semantic;

import java.util.List;
import java.util.Objects;

public record ParseResult(
        DeclarationIndex declarations,
        InvocationIndex invocations,
        List<ParseFailure> failures
) {
    public ParseResult {
        Objects.requireNonNull(declarations, "declarations");
        Objects.requireNonNull(invocations, "invocations");
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
