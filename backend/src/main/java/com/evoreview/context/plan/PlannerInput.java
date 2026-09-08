package com.evoreview.context.plan;

import com.evoreview.context.model.ChangedFile;
import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.semantic.ChangedSymbol;

import java.util.List;
import java.util.Map;

public record PlannerInput(
        List<ChangedFile> changedFiles,
        List<ChangedSymbol> changedSymbols,
        List<ContextEdge> edges,
        Map<String, String> headContents,
        Map<String, String> oldContents,
        Map<String, String> fileStats
) {
    public PlannerInput {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        changedSymbols = changedSymbols == null ? List.of() : List.copyOf(changedSymbols);
        edges = edges == null ? List.of() : List.copyOf(edges);
        headContents = headContents == null ? Map.of() : Map.copyOf(headContents);
        oldContents = oldContents == null ? Map.of() : Map.copyOf(oldContents);
        fileStats = fileStats == null ? Map.of() : Map.copyOf(fileStats);
    }
}
