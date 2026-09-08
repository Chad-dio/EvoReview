package com.evoreview.context.recall;

import com.evoreview.context.model.ChangedFile;
import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.EdgeType;
import com.evoreview.context.model.FileCategory;
import com.evoreview.context.model.RecallSource;
import com.evoreview.context.model.RevisionSide;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * S4 channel: naming-convention pairs like Foo.java ↔ FooTest.java. Cheap and
 * high precision. Edges always point test → production so "from" is the
 * related context and "to" is the change.
 */
@Component
public final class ConventionRecall implements RecallChannel {

    private static final double CONFIDENCE = 0.95;
    private static final List<String> TEST_SUFFIXES = List.of("Test", "Tests", "IT");

    private static final Comparator<ContextEdge> EDGE_ORDER = Comparator
            .comparing((ContextEdge edge) -> edge.from().path())
            .thenComparing(edge -> edge.to().path());

    @Override
    public RecallSource source() {
        return RecallSource.CONVENTION;
    }

    @Override
    public List<ContextEdge> recall(RecallInput input) {
        Set<String> files = new HashSet<>(input.headFiles());
        List<ContextEdge> edges = new ArrayList<>();
        for (ChangedFile file : input.changedFiles()) {
            if (!file.path().endsWith(".java")) {
                continue;
            }
            if (file.category() == FileCategory.SOURCE) {
                for (String candidate : testCandidates(file.path())) {
                    if (files.contains(candidate)) {
                        edges.add(edge(candidate, file.path()));
                    }
                }
            } else if (file.category() == FileCategory.TEST) {
                for (String candidate : mainCandidates(file.path())) {
                    if (files.contains(candidate)) {
                        edges.add(edge(file.path(), candidate));
                    }
                }
            }
        }
        edges.sort(EDGE_ORDER);
        return List.copyOf(edges);
    }

    private static ContextEdge edge(String testPath, String mainPath) {
        return new ContextEdge(
                EdgeType.TESTED_BY,
                ContextRef.file(RevisionSide.HEAD, testPath),
                ContextRef.file(RevisionSide.HEAD, mainPath),
                CONFIDENCE,
                RecallSource.CONVENTION);
    }

    static List<String> testCandidates(String sourcePath) {
        String testPath = sourcePath.replace("src/main/java/", "src/test/java/");
        String base = stripJavaExtension(testPath);
        return TEST_SUFFIXES.stream().map(suffix -> base + suffix + ".java").toList();
    }

    static List<String> mainCandidates(String testPath) {
        String mainPath = testPath.replace("src/test/java/", "src/main/java/");
        String base = stripJavaExtension(mainPath);
        List<String> candidates = new ArrayList<>();
        for (String suffix : TEST_SUFFIXES) {
            if (base.endsWith(suffix)) {
                candidates.add(base.substring(0, base.length() - suffix.length()) + ".java");
            }
        }
        return candidates;
    }

    private static String stripJavaExtension(String path) {
        return path.endsWith(".java") ? path.substring(0, path.length() - ".java".length()) : path;
    }
}
