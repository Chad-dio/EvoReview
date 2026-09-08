package com.evoreview.context.recall;

import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.model.EdgeType;
import com.evoreview.context.model.RecallSource;
import com.evoreview.context.model.RevisionSide;
import com.evoreview.context.semantic.ChangedSymbol;
import com.evoreview.context.semantic.DeclarationIndex;
import com.evoreview.context.semantic.FixtureRepo;
import com.evoreview.context.semantic.JavaAstProvider;
import com.evoreview.context.semantic.MethodDecl;
import com.evoreview.context.semantic.ParseResult;
import com.evoreview.context.semantic.TypeDecl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolGraphRecallTest {

    private final SymbolGraphRecall recall = new SymbolGraphRecall();
    private final ParseResult parse = new JavaAstProvider().parse(FixtureRepo.sources());

    @Test
    void findsCallersWithReceiverImportAndPackageConfidence() {
        ChangedSymbol save = symbol("com.example.service.FooService", "save", 1, RevisionSide.HEAD);

        List<ContextEdge> edges = recall.recall(input(List.of(save)));

        assertThat(edges).anySatisfy(edge -> {
            assertThat(edge.type()).isEqualTo(EdgeType.CALLS);
            assertThat(edge.from().path()).isEqualTo(FixtureRepo.FOO_CALLER);
            assertThat(edge.confidence()).isEqualTo(0.85);
            assertThat(edge.to().symbolId()).isEqualTo("com.example.service.FooService#save(User)");
            assertThat(edge.source()).isEqualTo(RecallSource.SYMBOL_GRAPH);
        });
        assertThat(edges).anySatisfy(edge -> {
            assertThat(edge.type()).isEqualTo(EdgeType.CALLS);
            assertThat(edge.from().path()).isEqualTo(FixtureRepo.ORDER_CALLER);
            assertThat(edge.confidence()).isEqualTo(0.7);
        });
    }

    @Test
    void tagsTestCallersAsTestedBy() {
        ChangedSymbol save = symbol("com.example.service.FooService", "save", 1, RevisionSide.HEAD);

        List<ContextEdge> edges = recall.recall(input(List.of(save)));

        assertThat(edges).anySatisfy(edge -> {
            assertThat(edge.type()).isEqualTo(EdgeType.TESTED_BY);
            assertThat(edge.from().path()).isEqualTo(FixtureRepo.FOO_SERVICE_TEST);
        });
    }

    @Test
    void dropsSameNameCallsThatCannotBeTheChangedMethod() {
        ChangedSymbol save = symbol("com.example.service.FooService", "save", 1, RevisionSide.HEAD);

        List<ContextEdge> edges = recall.recall(input(List.of(save)));

        assertThat(edges).noneMatch(edge -> edge.from().path().equals(FixtureRepo.REPO_CALLER));
    }

    @Test
    void changedInterfaceMethodFindsImplementations() {
        ChangedSymbol retry = symbol("com.example.policy.RetryPolicy", "retry", 1, RevisionSide.HEAD);

        List<ContextEdge> edges = recall.recall(input(List.of(retry)));

        assertThat(edges).anySatisfy(edge -> {
            assertThat(edge.type()).isEqualTo(EdgeType.OVERRIDES);
            assertThat(edge.from().path()).isEqualTo(FixtureRepo.DEFAULT_RETRY_POLICY);
            assertThat(edge.from().symbolId()).isEqualTo("com.example.policy.DefaultRetryPolicy#retry(int)");
            assertThat(edge.confidence()).isEqualTo(0.85);
        });
    }

    @Test
    void changedImplementationFindsInterfaceDeclaration() {
        ChangedSymbol retry = symbol("com.example.policy.DefaultRetryPolicy", "retry", 1, RevisionSide.HEAD);

        List<ContextEdge> edges = recall.recall(input(List.of(retry)));

        assertThat(edges).anySatisfy(edge -> {
            assertThat(edge.type()).isEqualTo(EdgeType.OVERRIDES);
            assertThat(edge.from().path()).isEqualTo(FixtureRepo.RETRY_POLICY);
            assertThat(edge.confidence()).isEqualTo(0.9);
        });
    }

    @Test
    void callersOfMergeBaseSideSymbolsGetConfidenceFloor() {
        ChangedSymbol deletedSave =
                symbol("com.example.service.FooService", "save", 1, RevisionSide.MERGE_BASE);

        List<ContextEdge> edges = recall.recall(input(List.of(deletedSave)));

        assertThat(edges).anySatisfy(edge -> {
            assertThat(edge.type()).isEqualTo(EdgeType.CALLS);
            assertThat(edge.from().path()).isEqualTo(FixtureRepo.FOO_CALLER);
            assertThat(edge.confidence()).isEqualTo(0.9);
            assertThat(edge.to().revision()).isEqualTo(RevisionSide.MERGE_BASE);
        });
    }

    @Test
    void recallIsDeterministic() {
        ChangedSymbol save = symbol("com.example.service.FooService", "save", 1, RevisionSide.HEAD);
        ChangedSymbol retry = symbol("com.example.policy.RetryPolicy", "retry", 1, RevisionSide.HEAD);

        List<ContextEdge> first = recall.recall(input(List.of(save, retry)));
        List<ContextEdge> second = recall.recall(input(List.of(retry, save)));

        assertThat(first).isEqualTo(second);
    }

    private ChangedSymbol symbol(String fqn, String methodName, int arity, RevisionSide side) {
        TypeDecl type = parse.declarations().byFqn(fqn).orElseThrow();
        MethodDecl method = type.methods().stream()
                .filter(candidate -> candidate.name().equals(methodName) && candidate.arity() == arity)
                .findFirst()
                .orElseThrow();
        return new ChangedSymbol(type.path(), side, fqn, method.name(), method.signature(),
                method.arity(), method.startLine(), method.endLine());
    }

    private RecallInput input(List<ChangedSymbol> symbols) {
        return new RecallInput(List.of(), symbols, parse.declarations(), parse.invocations(),
                DeclarationIndex.empty(), FixtureRepo.headFiles());
    }
}
