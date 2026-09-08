package com.evoreview.context.semantic;

import java.util.List;
import java.util.Objects;

public record OldSideAnalysis(
        DeclarationIndex declarations,
        List<ChangedSymbol> symbols,
        List<ParseFailure> failures
) {
    public OldSideAnalysis {
        Objects.requireNonNull(declarations, "declarations");
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
