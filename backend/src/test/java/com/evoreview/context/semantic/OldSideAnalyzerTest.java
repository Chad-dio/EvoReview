package com.evoreview.context.semantic;

import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.ChangedFile;
import com.evoreview.context.model.FileCategory;
import com.evoreview.context.model.Hunk;
import com.evoreview.context.model.HunkLine;
import com.evoreview.context.model.RevisionSide;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class OldSideAnalyzerTest {

    private static final String OLD_HELPER = "src/main/java/com/example/policy/OldHelper.java";

    private final OldSideAnalyzer analyzer = new OldSideAnalyzer(new JavaAstProvider());

    @Test
    void deletedFileYieldsAllItsOldMethods() {
        Map<String, String> oldContents = Map.of(OLD_HELPER, """
                package com.example.policy;

                public class OldHelper {
                    public void removed() {
                    }

                    public void alsoRemoved(int count) {
                    }
                }
                """);
        ChangedFile deleted = new ChangedFile(
                OLD_HELPER, null, ChangeType.DELETE, FileCategory.SOURCE, List.of());

        OldSideAnalysis analysis = analyzer.analyze(List.of(deleted), loader(oldContents));

        assertThat(analysis.failures()).isEmpty();
        assertThat(analysis.symbols())
                .extracting(ChangedSymbol::symbolId)
                .containsExactlyInAnyOrder(
                        "com.example.policy.OldHelper#removed()",
                        "com.example.policy.OldHelper#alsoRemoved(int)");
        assertThat(analysis.symbols())
                .allMatch(symbol -> symbol.side() == RevisionSide.MERGE_BASE);
        assertThat(analysis.declarations().byFqn("com.example.policy.OldHelper")).isPresent();
    }

    @Test
    void modifiedFileYieldsOnlyMethodsTouchingRemovedLines() {
        DeclarationIndex oldIndex = new JavaAstProvider()
                .parse(List.of(new SourceInput(FixtureRepo.FOO_SERVICE, FixtureRepo.contentOf(FixtureRepo.FOO_SERVICE))))
                .declarations();
        MethodDecl validate = oldIndex.byFqn("com.example.service.FooService").orElseThrow()
                .methods().stream()
                .filter(method -> method.name().equals("validate"))
                .findFirst()
                .orElseThrow();

        ChangedFile modified = new ChangedFile(
                FixtureRepo.FOO_SERVICE, null, ChangeType.MODIFY, FileCategory.SOURCE,
                List.of(new Hunk(validate.startLine(), 1, 1, 0,
                        List.of(HunkLine.removed(validate.startLine(), "    private void validate(User user) {")))));

        OldSideAnalysis analysis = analyzer.analyze(
                List.of(modified),
                path -> Optional.of(FixtureRepo.contentOf(FixtureRepo.FOO_SERVICE)));

        assertThat(analysis.symbols())
                .extracting(ChangedSymbol::methodName)
                .containsExactly("validate");
    }

    @Test
    void missingOldContentIsSkippedGracefully() {
        ChangedFile deleted = new ChangedFile(
                OLD_HELPER, null, ChangeType.DELETE, FileCategory.SOURCE, List.of());

        OldSideAnalysis analysis = analyzer.analyze(List.of(deleted), path -> Optional.empty());

        assertThat(analysis.symbols()).isEmpty();
        assertThat(analysis.failures()).isEmpty();
    }

    private static Function<String, Optional<String>> loader(Map<String, String> contents) {
        return path -> Optional.ofNullable(contents.get(path));
    }
}
