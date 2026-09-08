package com.evoreview.context.pack;

import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.DroppedItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Deterministic budgeted coverage greedy (design D9 — deliberately no
 * submodularity claim). Required items and their dependency closures are always
 * included; optional items are picked by marginalValue / marginalTokens where
 * both sides account for the not-yet-included requires closure. Ties break by
 * (path, startLine, itemId) so equal inputs pack byte-identically.
 */
public final class BudgetPacker {

    private static final Comparator<ContextItem> TIE_BREAK = Comparator
            .comparing((ContextItem item) -> item.ref().path())
            .thenComparing(item -> item.ref().startLine(), Comparator.nullsFirst(Integer::compareTo))
            .thenComparing(ContextItem::itemId);

    public PackResult pack(List<ContextItem> candidates, int budget, ToDoubleFunction<ContextItem> valueOf) {
        Map<String, ContextItem> byId = new LinkedHashMap<>();
        for (ContextItem item : candidates) {
            byId.putIfAbsent(item.itemId(), item);
        }

        List<ContextItem> included = new ArrayList<>();
        Set<String> includedIds = new HashSet<>();
        List<DroppedItem> dropped = new ArrayList<>();
        int used = 0;

        List<ContextItem> required = byId.values().stream()
                .filter(ContextItem::required)
                .sorted(TIE_BREAK)
                .toList();
        for (ContextItem item : required) {
            used += includeWithClosure(item, byId, included, includedIds);
        }

        List<ContextItem> optional = byId.values().stream()
                .filter(item -> !item.required())
                .sorted(TIE_BREAK)
                .toList();

        while (true) {
            ContextItem best = null;
            double bestRatio = 0.0;
            for (ContextItem item : optional) {
                if (includedIds.contains(item.itemId())) {
                    continue;
                }
                Set<ContextItem> closure = closureOf(item, byId, includedIds);
                int marginalTokens = closure.stream().mapToInt(ContextItem::estimatedTokens).sum();
                double marginalValue = closure.stream().mapToDouble(valueOf).sum();
                if (marginalTokens == 0) {
                    marginalTokens = 1;
                }
                if (used + marginalTokens > budget) {
                    continue;
                }
                double ratio = marginalValue / marginalTokens;
                if (best == null || ratio > bestRatio
                        || (ratio == bestRatio && TIE_BREAK.compare(item, best) < 0)) {
                    best = item;
                    bestRatio = ratio;
                }
            }
            if (best == null) {
                break;
            }
            used += includeWithClosure(best, byId, included, includedIds);
        }

        for (ContextItem item : optional) {
            if (!includedIds.contains(item.itemId())) {
                dropped.add(new DroppedItem(item.itemId(), "over-budget"));
            }
        }
        return new PackResult(included, dropped, used);
    }

    private static int includeWithClosure(
            ContextItem item, Map<String, ContextItem> byId,
            List<ContextItem> included, Set<String> includedIds) {
        Set<ContextItem> closure = closureOf(item, byId, includedIds);
        int tokens = 0;
        List<ContextItem> ordered = closure.stream().sorted(TIE_BREAK).toList();
        for (ContextItem member : ordered) {
            if (includedIds.add(member.itemId())) {
                included.add(member);
                tokens += member.estimatedTokens();
            }
        }
        return tokens;
    }

    private static Set<ContextItem> closureOf(
            ContextItem item, Map<String, ContextItem> byId, Set<String> includedIds) {
        Set<ContextItem> closure = new LinkedHashSet<>();
        List<ContextItem> queue = new ArrayList<>();
        queue.add(item);
        while (!queue.isEmpty()) {
            ContextItem current = queue.remove(queue.size() - 1);
            if (!closure.add(current)) {
                continue;
            }
            for (String dependencyId : current.requires()) {
                ContextItem dependency = byId.get(dependencyId);
                if (dependency != null && !includedIds.contains(dependency.itemId())
                        && !closure.contains(dependency)) {
                    queue.add(dependency);
                }
            }
        }
        closure.removeIf(member -> includedIds.contains(member.itemId()));
        return closure;
    }
}
