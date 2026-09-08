package com.evoreview.context.semantic;

import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.ChangedFile;
import com.evoreview.context.model.FileCategory;
import com.evoreview.context.model.Hunk;
import com.evoreview.context.model.HunkLine;
import com.evoreview.context.model.RevisionSide;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangedSymbolLocatorTest {

    private final ChangedSymbolLocator locator = new ChangedSymbolLocator();
    private final DeclarationIndex headIndex =
            new JavaAstProvider().parse(FixtureRepo.sources()).declarations();

    @Test
    void locatesMethodContainingChangedLine() {
        MethodDecl save = method("com.example.service.FooService", "save", 1);
        ChangedFile file = changedFile(FixtureRepo.FOO_SERVICE,
                HunkLine.added(save.startLine() + 1, "// touched"));

        List<ChangedSymbol> symbols = locator.locateHead(file, headIndex);

        assertThat(symbols).hasSize(1);
        ChangedSymbol symbol = symbols.get(0);
        assertThat(symbol.symbolId()).isEqualTo("com.example.service.FooService#save(User)");
        assertThat(symbol.arity()).isEqualTo(1);
        assertThat(symbol.side()).isEqualTo(RevisionSide.HEAD);
    }

    @Test
    void doesNotTouchSiblingMethods() {
        MethodDecl validate = method("com.example.service.FooService", "validate", 1);
        ChangedFile file = changedFile(FixtureRepo.FOO_SERVICE,
                HunkLine.added(validate.startLine() + 1, "// touched"));

        List<ChangedSymbol> symbols = locator.locateHead(file, headIndex);

        assertThat(symbols).extracting(ChangedSymbol::methodName).containsExactly("validate");
    }

    @Test
    void ignoresPureRemovalsOnHeadSide() {
        MethodDecl save = method("com.example.service.FooService", "save", 1);
        ChangedFile file = changedFile(FixtureRepo.FOO_SERVICE,
                HunkLine.removed(save.startLine() + 1, "validate(user);"));

        assertThat(locator.locateHead(file, headIndex)).isEmpty();
    }

    @Test
    void deletedFileYieldsAllOldMethodsOnMergeBaseSide() {
        DeclarationIndex oldIndex = headIndex;
        ChangedFile deleted = new ChangedFile(
                FixtureRepo.FOO_SERVICE, null, ChangeType.DELETE, FileCategory.SOURCE, List.of());

        List<ChangedSymbol> symbols = locator.locateOldSide(deleted, oldIndex);

        assertThat(symbols)
                .extracting(ChangedSymbol::symbolId)
                .containsExactlyInAnyOrder(
                        "com.example.service.FooService#save(User)",
                        "com.example.service.FooService#save(User,boolean)",
                        "com.example.service.FooService#validate(User)");
        assertThat(symbols).allMatch(symbol -> symbol.side() == RevisionSide.MERGE_BASE);
    }

    @Test
    void removedLinesLocateOldSideMethod() {
        MethodDecl validate = method("com.example.service.FooService", "validate", 1);
        ChangedFile file = changedFile(FixtureRepo.FOO_SERVICE,
                HunkLine.removed(validate.startLine(), "private void validate(User user) {"));

        List<ChangedSymbol> symbols = locator.locateOldSide(file, headIndex);

        assertThat(symbols).extracting(ChangedSymbol::methodName).containsExactly("validate");
        assertThat(symbols.get(0).side()).isEqualTo(RevisionSide.MERGE_BASE);
    }

    @Test
    void skipsNonJavaAndNonSourceFiles() {
        ChangedFile markdown = new ChangedFile(
                "README.md", null, ChangeType.MODIFY, FileCategory.DOCUMENTATION,
                List.of(new Hunk(1, 1, 1, 1, List.of(HunkLine.added(1, "x")))));

        assertThat(locator.locateHead(markdown, headIndex)).isEmpty();
    }

    private MethodDecl method(String fqn, String name, int arity) {
        return headIndex.byFqn(fqn).orElseThrow().methods().stream()
                .filter(method -> method.name().equals(name) && method.arity() == arity)
                .findFirst()
                .orElseThrow();
    }

    private static ChangedFile changedFile(String path, HunkLine line) {
        return new ChangedFile(path, null, ChangeType.MODIFY, FileCategory.SOURCE,
                List.of(new Hunk(1, 1, 1, 2, List.of(line))));
    }
}
