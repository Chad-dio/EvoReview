package com.evoreview.context;

import com.evoreview.context.acquisition.PatchAcquisition;
import com.evoreview.context.acquisition.PatchProvider;
import com.evoreview.context.acquisition.RawFilePatch;
import com.evoreview.context.acquisition.RawPatch;
import com.evoreview.context.acquisition.RepoSnapshot;
import com.evoreview.context.acquisition.RepoSnapshotProvider;
import com.evoreview.context.model.Capability;
import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.ChannelState;
import com.evoreview.context.model.ContextBuildResult;
import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.context.plan.ContextPlanner;
import com.evoreview.context.rank.HeuristicContextPolicy;
import com.evoreview.context.recall.ConventionRecall;
import com.evoreview.context.recall.SymbolGraphRecall;
import com.evoreview.context.semantic.ChangedSymbolLocator;
import com.evoreview.context.semantic.JavaAstProvider;
import com.evoreview.context.semantic.OldSideAnalyzer;
import com.evoreview.context.parse.SensitiveFileClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextBuilderTest {

    private static final String A_PATH = "src/main/java/com/x/A.java";

    private static final String A_HEAD = """
            package com.x;

            public class A {

                public void m() {
                    int a = 1;
                    int b = 2;
                }
            }
            """;

    private static final String A_OLD = """
            package com.x;

            public class A {

                public void m() {
                    int a = 1;
                }
            }
            """;

    private static final String A_PATCH = """
            @@ -3,6 +3,7 @@
             public class A {
            \s
                 public void m() {
                     int a = 1;
            +        int b = 2;
                 }
             }
            """;

    @TempDir
    Path tempDir;

    private PatchProvider patchProvider;
    private RepoSnapshotProvider snapshotProvider;
    private ContextBuilder builder;

    @BeforeEach
    void setUp() throws Exception {
        patchProvider = mock(PatchProvider.class);
        snapshotProvider = mock(RepoSnapshotProvider.class);

        when(patchProvider.fetch(7L, "o", "r", 42)).thenReturn(acquisition());
        when(snapshotProvider.openHeadSnapshot(7L, "o", "r", "head999"))
                .thenReturn(mapSnapshot(Map.of(A_PATH, A_HEAD)));
        when(snapshotProvider.mergeBaseFileContent(7L, "o", "r", "merge00", A_PATH))
                .thenReturn(Optional.of(A_OLD));

        ContextProperties properties = new ContextProperties();
        properties.setStoreDir(tempDir.resolve("contexts").toString());

        builder = new ContextBuilder(
                patchProvider, snapshotProvider, new JavaAstProvider(),
                new ChangedSymbolLocator(), new OldSideAnalyzer(new JavaAstProvider()),
                List.of(new SymbolGraphRecall(), new ConventionRecall()),
                new ContextPlanner(new CharsDivFourTokenEstimator(), properties,
                        SensitiveFileClassifier.withDefaults()),
                new FileContextStore(properties),
                properties, new CharsDivFourTokenEstimator(), new HeuristicContextPolicy());
    }

    @Test
    void buildsFrozenPlanWithRequiredItemsAndCapabilities() {
        ContextBuildResult result = builder.build(7L, "o", "r", 42);

        assertThat(result).isInstanceOf(ContextBuildResult.Ready.class);
        ReviewPlan plan = ((ContextBuildResult.Ready) result).plan();

        assertThat(plan.contextId()).hasSize(64);
        assertThat(plan.revision().mergeBaseSha()).isEqualTo("merge00");
        assertThat(plan.slices()).hasSize(1);

        List<String> includedIds = plan.slices().get(0).included().stream()
                .map(ContextItem::itemId)
                .toList();
        assertThat(includedIds)
                .contains("DIFF#" + A_PATH + "#0", "METHOD#com.x.A#m()", "CLASS#com.x.A");

        assertThat(plan.capabilities().patch()).isEqualTo(Capability.COMPLETE);
        assertThat(plan.capabilities().headSnapshot()).isEqualTo(Capability.COMPLETE);
        assertThat(plan.capabilities().mergeBaseFiles()).isEqualTo(Capability.COMPLETE);
        assertThat(plan.capabilities().astCoverage()).isEqualTo(1.0);
        assertThat(plan.capabilities().symbolRecall()).isEqualTo(ChannelState.ENABLED);
        assertThat(plan.capabilities().coChange()).isEqualTo(ChannelState.DISABLED_BY_CONFIG);

        assertThat(plan.summary().fileCount()).isEqualTo(1);
        assertThat(plan.summary().additions()).isEqualTo(1);
    }

    @Test
    void secondBuildWithSameInputsHitsTheFrozenSnapshot() throws Exception {
        ContextBuildResult first = builder.build(7L, "o", "r", 42);
        ContextBuildResult second = builder.build(7L, "o", "r", 42);

        assertThat(((ContextBuildResult.Ready) second).plan().contextId())
                .isEqualTo(((ContextBuildResult.Ready) first).plan().contextId());
        verify(snapshotProvider, times(1))
                .openHeadSnapshot(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void providerFailureYieldsSkippedInsteadOfThrowing() throws Exception {
        when(patchProvider.fetch(anyLong(), anyString(), anyString(), eq(42)))
                .thenThrow(new IOException("github down"));

        ContextBuildResult result = builder.build(7L, "o", "r", 42);

        assertThat(result).isInstanceOf(ContextBuildResult.Skipped.class);
        assertThat(((ContextBuildResult.Skipped) result).reason()).contains("github down");
    }

    private static PatchAcquisition acquisition() {
        RawFilePatch file = new RawFilePatch(
                A_PATH, null, ChangeType.MODIFY, A_PATCH, 1, 0);
        return new PatchAcquisition(
                new com.evoreview.context.model.RevisionSpec(
                        "o/r", 42, "base111", "merge00", "head999", "patchff"),
                new RawPatch("o/r", 42, List.of(file)));
    }

    private static RepoSnapshot mapSnapshot(Map<String, String> contents) {
        return new RepoSnapshot() {
            @Override
            public Kind kind() {
                return Kind.ARCHIVE_FULL;
            }

            @Override
            public List<String> listFiles() {
                return contents.keySet().stream().sorted().toList();
            }

            @Override
            public Optional<String> readFile(String path) {
                return Optional.ofNullable(contents.get(path));
            }
        };
    }
}
