package com.evoreview.context.semantic;

import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.ChangedFile;
import com.evoreview.context.model.FileCategory;
import com.evoreview.context.model.HunkLine;
import com.evoreview.context.model.LineType;
import com.evoreview.context.model.RevisionSide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Maps changed lines to the methods that contain them. Head side uses
 * ADDED/CONTEXT new-line numbers; merge-base side uses REMOVED old-line
 * numbers. A deleted file with no patch contributes all of its old methods.
 */
public final class ChangedSymbolLocator {

    public List<ChangedSymbol> locateHead(ChangedFile file, DeclarationIndex headIndex) {
        return locate(file, headIndex, RevisionSide.HEAD, changedNewLines(file));
    }

    public List<ChangedSymbol> locateOldSide(ChangedFile file, DeclarationIndex oldIndex) {
        if (file.changeType() == ChangeType.DELETE) {
            return locateAll(file, oldIndex);
        }
        return locate(file, oldIndex, RevisionSide.MERGE_BASE, removedOldLines(file));
    }

    private List<ChangedSymbol> locate(
            ChangedFile file, DeclarationIndex index, RevisionSide side, Set<Integer> lines) {
        if (file.category() != FileCategory.SOURCE || !file.path().endsWith(".java") || lines.isEmpty()) {
            return List.of();
        }
        String lookupPath = oldPathOf(file, side);
        List<ChangedSymbol> symbols = new ArrayList<>();
        for (TypeDecl type : index.byPath(lookupPath)) {
            for (MethodDecl method : type.methods()) {
                boolean hit = lines.stream().anyMatch(method::containsLine);
                if (hit) {
                    symbols.add(toChangedSymbol(lookupPath, side, type, method));
                }
            }
        }
        return sorted(symbols);
    }

    private List<ChangedSymbol> locateAll(ChangedFile file, DeclarationIndex index) {
        if (file.category() != FileCategory.SOURCE || !file.path().endsWith(".java")) {
            return List.of();
        }
        String lookupPath = oldPathOf(file, RevisionSide.MERGE_BASE);
        List<ChangedSymbol> symbols = new ArrayList<>();
        for (TypeDecl type : index.byPath(lookupPath)) {
            for (MethodDecl method : type.methods()) {
                symbols.add(toChangedSymbol(lookupPath, RevisionSide.MERGE_BASE, type, method));
            }
        }
        return sorted(symbols);
    }

    private static String oldPathOf(ChangedFile file, RevisionSide side) {
        if (side == RevisionSide.MERGE_BASE && file.previousPath() != null) {
            return file.previousPath();
        }
        return file.path();
    }

    private static Set<Integer> changedNewLines(ChangedFile file) {
        Set<Integer> lines = new TreeSet<>();
        file.hunks().forEach(hunk -> hunk.lines().stream()
                .filter(line -> line.type() != LineType.REMOVED)
                .map(HunkLine::newLineNo)
                .forEach(lines::add));
        return lines;
    }

    private static Set<Integer> removedOldLines(ChangedFile file) {
        Set<Integer> lines = new TreeSet<>();
        file.hunks().forEach(hunk -> hunk.lines().stream()
                .filter(line -> line.type() == LineType.REMOVED)
                .map(HunkLine::oldLineNo)
                .forEach(lines::add));
        return lines;
    }

    private static ChangedSymbol toChangedSymbol(
            String path, RevisionSide side, TypeDecl type, MethodDecl method) {
        return new ChangedSymbol(
                path, side, type.fqn(), method.name(), method.signature(),
                method.arity(), method.startLine(), method.endLine());
    }

    private static List<ChangedSymbol> sorted(List<ChangedSymbol> symbols) {
        symbols.sort(Comparator.comparing(ChangedSymbol::path)
                .thenComparing(ChangedSymbol::startLine)
                .thenComparing(ChangedSymbol::symbolId));
        return List.copyOf(symbols);
    }
}
