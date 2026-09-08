package com.evoreview.context.recall;

import com.evoreview.context.model.ChangedFile;
import com.evoreview.context.semantic.ChangedSymbol;
import com.evoreview.context.semantic.DeclarationIndex;
import com.evoreview.context.semantic.InvocationIndex;

import java.util.List;
import java.util.Objects;

/**
 * Everything a recall channel may read. oldDeclarations is empty when no
 * merge-base parsing ran; channels must tolerate both sides being partial.
 */
public record RecallInput(
        List<ChangedFile> changedFiles,
        List<ChangedSymbol> changedSymbols,
        DeclarationIndex declarations,
        InvocationIndex invocations,
        DeclarationIndex oldDeclarations,
        List<String> headFiles
) {
    public RecallInput {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        changedSymbols = changedSymbols == null ? List.of() : List.copyOf(changedSymbols);
        declarations = declarations == null ? DeclarationIndex.empty() : declarations;
        invocations = invocations == null ? InvocationIndex.empty() : invocations;
        oldDeclarations = oldDeclarations == null ? DeclarationIndex.empty() : oldDeclarations;
        headFiles = headFiles == null ? List.of() : List.copyOf(headFiles);
    }

    public DeclarationIndex declarationsFor(ChangedSymbol symbol) {
        return switch (symbol.side()) {
            case HEAD -> declarations;
            case MERGE_BASE -> oldDeclarations;
        };
    }
}
