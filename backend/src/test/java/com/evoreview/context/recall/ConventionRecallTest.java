package com.evoreview.context.recall;

import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.ChangedFile;
import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.model.EdgeType;
import com.evoreview.context.model.FileCategory;
import com.evoreview.context.model.RecallSource;
import com.evoreview.context.semantic.DeclarationIndex;
import com.evoreview.context.semantic.FixtureRepo;
import com.evoreview.context.semantic.InvocationIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConventionRecallTest {

    private final ConventionRecall recall = new ConventionRecall();

    @Test
    void sourceFileFindsItsTestClass() {
        List<ContextEdge> edges = recall.recall(input(changed(FixtureRepo.FOO_SERVICE, FileCategory.SOURCE)));

        assertThat(edges).hasSize(1);
        ContextEdge edge = edges.get(0);
        assertThat(edge.type()).isEqualTo(EdgeType.TESTED_BY);
        assertThat(edge.from().path()).isEqualTo(FixtureRepo.FOO_SERVICE_TEST);
        assertThat(edge.to().path()).isEqualTo(FixtureRepo.FOO_SERVICE);
        assertThat(edge.confidence()).isEqualTo(0.95);
        assertThat(edge.source()).isEqualTo(RecallSource.CONVENTION);
    }

    @Test
    void testFileFindsItsProductionClass() {
        List<ContextEdge> edges = recall.recall(input(changed(FixtureRepo.FOO_SERVICE_TEST, FileCategory.TEST)));

        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).from().path()).isEqualTo(FixtureRepo.FOO_SERVICE_TEST);
        assertThat(edges.get(0).to().path()).isEqualTo(FixtureRepo.FOO_SERVICE);
    }

    @Test
    void noMatchingConventionMeansNoEdges() {
        List<ContextEdge> edges = recall.recall(input(changed(FixtureRepo.USER, FileCategory.SOURCE)));

        assertThat(edges).isEmpty();
    }

    @Test
    void nonJavaFilesAreIgnored() {
        List<ContextEdge> edges = recall.recall(input(changed("frontend/app/foo.ts", FileCategory.SOURCE)));

        assertThat(edges).isEmpty();
    }

    private static ChangedFile changed(String path, FileCategory category) {
        return new ChangedFile(path, null, ChangeType.MODIFY, category, List.of());
    }

    private static RecallInput input(ChangedFile file) {
        return new RecallInput(List.of(file), List.of(), DeclarationIndex.empty(),
                InvocationIndex.empty(), DeclarationIndex.empty(), FixtureRepo.headFiles());
    }
}
