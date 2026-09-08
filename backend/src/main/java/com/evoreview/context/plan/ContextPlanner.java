package com.evoreview.context.plan;

import com.evoreview.context.ContextProperties;
import com.evoreview.context.TokenEstimator;
import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.ChangedFile;
import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.Hunk;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.RecallSource;
import com.evoreview.context.model.RevisionSide;
import com.evoreview.context.parse.SensitiveFileClassifier;
import com.evoreview.context.semantic.ChangedSymbol;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Assembles the candidate ContextItem pool: required diff hunks and changed
 * method bodies, their class-header dependencies, and related-code items from
 * recall edges. Sensitive paths never contribute content — a withheld marker
 * is emitted instead. Output is sorted by itemId for determinism.
 */
@Component
public class ContextPlanner {

    private static final Pattern TYPE_DECLARATION =
            Pattern.compile("^.*\\b(class|interface|enum|record|@interface)\\s+\\w+.*$");

    private final TokenEstimator tokenEstimator;
    private final SensitiveFileClassifier sensitiveClassifier;
    private final long maxFileBytes;

    public ContextPlanner(
            TokenEstimator tokenEstimator,
            ContextProperties properties,
            SensitiveFileClassifier sensitiveClassifier
    ) {
        this.tokenEstimator = tokenEstimator;
        this.sensitiveClassifier = sensitiveClassifier;
        this.maxFileBytes = properties.getMaxFileKb() * 1024;
    }

    public PlannedContext plan(PlannerInput input) {
        Map<String, ContextItem> items = new LinkedHashMap<>();
        Map<String, String> owners = new LinkedHashMap<>();
        List<String> sensitiveSkipped = new ArrayList<>();
        Set<String> changedPaths = input.changedFiles().stream()
                .map(ChangedFile::path)
                .collect(Collectors.toCollection(HashSet::new));

        for (ChangedFile file : input.changedFiles()) {
            if (sensitiveClassifier.isSensitive(file.path())) {
                sensitiveSkipped.add(file.path());
                put(items, new ContextItem(
                        "FILE#" + file.path(), ItemKind.METADATA,
                        ContextRef.file(RevisionSide.HEAD, file.path()),
                        file.path() + " (sensitive file, content withheld)",
                        tokenEstimator.estimate(file.path()), true,
                        List.of(), List.of(RecallSource.REQUIRED), Map.of()));
                continue;
            }
            if (file.hunks().isEmpty()) {
                String stats = input.fileStats().getOrDefault(file.path(), "no patch available");
                put(items, new ContextItem(
                        "FILE#" + file.path(), ItemKind.METADATA,
                        ContextRef.file(RevisionSide.HEAD, file.path()),
                        file.path() + " (" + stats + ")",
                        tokenEstimator.estimate(stats), true,
                        List.of(), List.of(RecallSource.REQUIRED), Map.of()));
                continue;
            }
            RevisionSide side = file.changeType() == ChangeType.DELETE
                    ? RevisionSide.MERGE_BASE : RevisionSide.HEAD;
            int index = 0;
            for (Hunk hunk : file.hunks()) {
                String content = renderHunk(hunk);
                put(items, new ContextItem(
                        "DIFF#" + file.path() + "#" + index++, ItemKind.DIFF_HUNK,
                        hunkRef(side, file.path(), hunk), content,
                        tokenEstimator.estimate(content), true,
                        List.of(), List.of(RecallSource.REQUIRED), Map.of()));
            }
        }

        Set<String> classHeaders = new HashSet<>();
        for (ChangedSymbol symbol : input.changedSymbols()) {
            String content = contentFor(input, symbol.side(), symbol.path());
            if (content == null) {
                continue;
            }
            String classItemId = "CLASS#" + symbol.classFqn();
            String methodSource = extractLines(content, symbol.startLine(), symbol.endLine());
            put(items, new ContextItem(
                    "METHOD#" + symbol.symbolId(), ItemKind.CHANGED_METHOD,
                    new ContextRef(symbol.side(), symbol.path(), symbol.startLine(), symbol.endLine(),
                            symbol.symbolId(), null),
                    methodSource, tokenEstimator.estimate(methodSource), true,
                    List.of(classItemId), List.of(RecallSource.REQUIRED), Map.of()));

            if (classHeaders.add(classItemId)) {
                String header = classHeader(content, simpleName(symbol.classFqn()));
                put(items, new ContextItem(
                        classItemId, ItemKind.METADATA,
                        ContextRef.file(symbol.side(), symbol.path()),
                        header, tokenEstimator.estimate(header), false,
                        List.of(), List.of(RecallSource.REQUIRED), Map.of()));
                owners.put(classItemId, symbol.path());
            }
        }

        for (ContextEdge edge : input.edges()) {
            ContextRef from = edge.from();
            if (changedPaths.contains(from.path()) || sensitiveClassifier.isSensitive(from.path())) {
                continue;
            }
            Map<String, String> contents =
                    from.revision() == RevisionSide.HEAD ? input.headContents() : input.oldContents();
            String fileContent = contents.get(from.path());
            if (fileContent == null) {
                continue;
            }
            String content = from.startLine() != null
                    ? extractLines(fileContent, from.startLine(), from.endLine())
                    : bounded(fileContent);
            ItemKind kind = switch (edge.type()) {
                case CALLS -> ItemKind.CALLER;
                case OVERRIDES -> ItemKind.OVERRIDE;
                case TESTED_BY -> ItemKind.TEST;
            };
            String itemId = kind.name() + "#" + from.path() + "#"
                    + (from.startLine() == null ? "file" : from.startLine());
            ContextItem item = new ContextItem(
                    itemId, kind, from, content, tokenEstimator.estimate(content), false,
                    List.of(), List.of(edge.source()), Map.of("confidence", edge.confidence()));
            ContextItem existing = items.get(itemId);
            if (existing != null) {
                if (edge.confidence() > existing.features().getOrDefault("confidence", 0.0)) {
                    put(items, item);
                }
            } else {
                put(items, item);
            }
            owners.putIfAbsent(itemId, edge.to().path());
        }

        List<ContextItem> sorted = items.values().stream()
                .sorted(Comparator.comparing(ContextItem::itemId))
                .toList();
        return new PlannedContext(sorted, owners, sensitiveSkipped);
    }

    private static void put(Map<String, ContextItem> items, ContextItem item) {
        items.put(item.itemId(), item);
    }

    private static ContextRef hunkRef(RevisionSide side, String path, Hunk hunk) {
        if (side == RevisionSide.MERGE_BASE) {
            int end = Math.max(hunk.oldStart(), hunk.oldStart() + hunk.oldLines() - 1);
            return ContextRef.lines(side, path, hunk.oldStart(), end);
        }
        int end = Math.max(hunk.newStart(), hunk.newStart() + hunk.newLines() - 1);
        return ContextRef.lines(side, path, hunk.newStart(), end);
    }

    private static String renderHunk(Hunk hunk) {
        String header = "@@ -" + hunk.oldStart() + "," + hunk.oldLines()
                + " +" + hunk.newStart() + "," + hunk.newLines() + " @@";
        String body = hunk.lines().stream()
                .map(line -> switch (line.type()) {
                    case CONTEXT -> " ";
                    case ADDED -> "+";
                    case REMOVED -> "-";
                } + line.content())
                .collect(Collectors.joining("\n"));
        return header + "\n" + body;
    }

    private static String contentFor(PlannerInput input, RevisionSide side, String path) {
        return (side == RevisionSide.HEAD ? input.headContents() : input.oldContents()).get(path);
    }

    private static String extractLines(String content, int startLine, int endLine) {
        String[] lines = content.split("\n");
        int from = Math.max(1, startLine);
        int to = Math.min(lines.length, Math.max(startLine, endLine));
        if (from > to || from > lines.length) {
            return "";
        }
        return String.join("\n", Arrays.copyOfRange(lines, from - 1, to));
    }

    private String bounded(String content) {
        if (content.length() <= maxFileBytes) {
            return content;
        }
        return content.substring(0, (int) maxFileBytes) + "\n… (truncated)";
    }

    private static String classHeader(String content, String simpleName) {
        List<String> kept = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
                kept.add(line);
            } else if (TYPE_DECLARATION.matcher(trimmed).matches() && trimmed.contains(simpleName)) {
                kept.add(line);
                break;
            }
        }
        return String.join("\n", kept);
    }

    private static String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }
}
