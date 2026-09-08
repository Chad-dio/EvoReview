package com.evoreview.context.pack;

import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.model.ContextItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Groups changed files into review slices. Union-find over changed files linked
 * by recall edges, then package cohesion for the singletons. A cluster whose
 * required items exceed the slice budget is split per file so the
 * required-always-included invariant stays satisfiable inside every slice.
 */
public final class ReviewSlicer {

    public List<SliceAssignment> slice(
            List<ContextItem> requiredItems, List<ContextEdge> edges, int sliceBudget) {
        Set<String> changedPaths = requiredItems.stream()
                .map(item -> item.ref().path())
                .collect(Collectors.toCollection(TreeSet::new));
        if (changedPaths.isEmpty()) {
            return List.of();
        }

        UnionFind unionFind = new UnionFind(changedPaths);
        for (ContextEdge edge : edges) {
            String from = edge.from().path();
            String to = edge.to().path();
            if (changedPaths.contains(from) && changedPaths.contains(to)) {
                unionFind.union(from, to);
            }
        }

        Map<String, List<String>> clusters = new LinkedHashMap<>();
        for (String path : changedPaths) {
            clusters.computeIfAbsent(unionFind.find(path), k -> new ArrayList<>()).add(path);
        }

        Map<String, Integer> requiredTokensByPath = new HashMap<>();
        for (ContextItem item : requiredItems) {
            requiredTokensByPath.merge(item.ref().path(), item.estimatedTokens(), Integer::sum);
        }

        List<SliceAssignment> assignments = new ArrayList<>();
        Map<String, List<String>> byPackage = new LinkedHashMap<>();
        for (List<String> cluster : clusters.values()) {
            if (cluster.size() > 1) {
                assignments.addAll(toSlices(cluster, requiredTokensByPath, sliceBudget));
            } else {
                byPackage.computeIfAbsent(parentDir(cluster.get(0)), k -> new ArrayList<>())
                        .add(cluster.get(0));
            }
        }
        for (List<String> packageGroup : byPackage.values()) {
            assignments.addAll(toSlices(packageGroup, requiredTokensByPath, sliceBudget));
        }

        List<SliceAssignment> sorted = assignments.stream()
                .map(assignment -> new SliceAssignment(assignment.sliceId(), sortedCopy(assignment.changedPaths())))
                .sorted(Comparator.comparing(assignment -> assignment.changedPaths().get(0)))
                .toList();
        List<SliceAssignment> numbered = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            numbered.add(new SliceAssignment("slice-" + (i + 1), sorted.get(i).changedPaths()));
        }
        return List.copyOf(numbered);
    }

    private List<SliceAssignment> toSlices(
            List<String> paths, Map<String, Integer> requiredTokensByPath, int sliceBudget) {
        long clusterTokens = paths.stream()
                .mapToLong(path -> requiredTokensByPath.getOrDefault(path, 0))
                .sum();
        if (paths.size() > 1 && clusterTokens > sliceBudget) {
            return paths.stream()
                    .map(path -> new SliceAssignment("", List.of(path)))
                    .toList();
        }
        return List.of(new SliceAssignment("", paths));
    }

    private static String parentDir(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static List<String> sortedCopy(List<String> paths) {
        return paths.stream().sorted().toList();
    }

    private static final class UnionFind {
        private final Map<String, String> parent = new HashMap<>();

        UnionFind(Set<String> elements) {
            elements.forEach(element -> parent.put(element, element));
        }

        String find(String element) {
            String root = parent.get(element);
            if (!root.equals(element)) {
                root = find(root);
                parent.put(element, root);
            }
            return root;
        }

        void union(String a, String b) {
            String rootA = find(a);
            String rootB = find(b);
            if (!rootA.equals(rootB)) {
                parent.put(rootA.compareTo(rootB) <= 0 ? rootA : rootB,
                        rootA.compareTo(rootB) <= 0 ? rootB : rootA);
            }
        }
    }
}
