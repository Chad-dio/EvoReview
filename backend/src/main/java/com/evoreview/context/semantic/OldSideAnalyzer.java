package com.evoreview.context.semantic;

import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.ChangedFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Parses the merge-base version of changed Java files so deleted methods,
 * removed signatures, and old implementations stay visible to the reviewer.
 * The content loader is the only I/O seam — backed by the contents API in
 * production, by an in-memory map in tests.
 */
public final class OldSideAnalyzer {

    private final AstProvider astProvider;
    private final ChangedSymbolLocator locator = new ChangedSymbolLocator();

    public OldSideAnalyzer(AstProvider astProvider) {
        this.astProvider = astProvider;
    }

    public OldSideAnalysis analyze(
            List<ChangedFile> files, Function<String, Optional<String>> oldContentLoader) {
        List<ChangedFile> oldSideFiles = files.stream()
                .filter(file -> file.changeType() != ChangeType.ADD)
                .filter(file -> file.path().endsWith(".java"))
                .toList();

        List<SourceInput> oldSources = new ArrayList<>();
        for (ChangedFile file : oldSideFiles) {
            String oldPath = file.previousPath() != null ? file.previousPath() : file.path();
            oldContentLoader.apply(oldPath)
                    .ifPresent(content -> oldSources.add(new SourceInput(oldPath, content)));
        }

        ParseResult parse = astProvider.parse(oldSources);
        List<ChangedSymbol> symbols = new ArrayList<>();
        for (ChangedFile file : oldSideFiles) {
            symbols.addAll(locator.locateOldSide(file, parse.declarations()));
        }
        return new OldSideAnalysis(parse.declarations(), symbols, parse.failures());
    }
}
