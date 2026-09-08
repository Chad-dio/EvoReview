package com.evoreview.context.pack;

import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.EdgeType;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.RecallSource;
import com.evoreview.context.model.RevisionSide;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewSlicerTest {

    private final ReviewSlicer slicer = new ReviewSlicer();

    @Test
    void edgeConnectedFilesShareOneSlice() {
        List<ContextItem> required = List.of(
                requiredDiff("src/main/java/a/Foo.java", 10),
                requiredDiff("src/main/java/b/Bar.java", 10));
        List<ContextEdge> edges = List.of(edge("src/main/java/a/Foo.java", "src/main/java/b/Bar.java"));

        List<SliceAssignment> slices = slicer.slice(required, edges, 1000);

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).sliceId()).isEqualTo("slice-1");
        assertThat(slices.get(0).changedPaths())
                .containsExactly("src/main/java/a/Foo.java", "src/main/java/b/Bar.java");
    }

    @Test
    void unconnectedFilesInSamePackageClusterTogether() {
        List<ContextItem> required = List.of(
                requiredDiff("src/main/java/a/Foo.java", 10),
                requiredDiff("src/main/java/a/Bar.java", 10),
                requiredDiff("src/main/java/b/Baz.java", 10));

        List<SliceAssignment> slices = slicer.slice(required, List.of(), 1000);

        assertThat(slices).hasSize(2);
        assertThat(slices.get(0).changedPaths())
                .containsExactly("src/main/java/a/Bar.java", "src/main/java/a/Foo.java");
        assertThat(slices.get(1).changedPaths()).containsExactly("src/main/java/b/Baz.java");
    }

    @Test
    void overBudgetClusterSplitsPerFile() {
        List<ContextItem> required = List.of(
                requiredDiff("src/main/java/a/Foo.java", 100),
                requiredDiff("src/main/java/a/Bar.java", 100));
        List<ContextEdge> edges = List.of(edge("src/main/java/a/Foo.java", "src/main/java/a/Bar.java"));

        List<SliceAssignment> slices = slicer.slice(required, edges, 150);

        assertThat(slices).hasSize(2);
        assertThat(slices.get(0).changedPaths()).containsExactly("src/main/java/a/Bar.java");
        assertThat(slices.get(1).changedPaths()).containsExactly("src/main/java/a/Foo.java");
    }

    @Test
    void emptyInputProducesNoSlices() {
        assertThat(slicer.slice(List.of(), List.of(), 1000)).isEmpty();
    }

    private static ContextItem requiredDiff(String path, int tokens) {
        return new ContextItem("DIFF#" + path + "#0", ItemKind.DIFF_HUNK,
                ContextRef.lines(RevisionSide.HEAD, path, 1, 2), "patch", tokens, true,
                List.of(), List.of(RecallSource.REQUIRED), Map.of());
    }

    private static ContextEdge edge(String fromPath, String toPath) {
        return new ContextEdge(EdgeType.CALLS,
                ContextRef.file(RevisionSide.HEAD, fromPath),
                ContextRef.file(RevisionSide.HEAD, toPath),
                0.9, RecallSource.SYMBOL_GRAPH);
    }
}
