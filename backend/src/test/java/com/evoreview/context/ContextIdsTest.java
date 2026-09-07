package com.evoreview.context;

import com.evoreview.context.acquisition.RawFilePatch;
import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.RevisionSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextIdsTest {

    private static final RawFilePatch FILE_A =
            new RawFilePatch("src/A.java", null, ChangeType.MODIFY, "@@ -1 +1 @@\n-a\n+b\n", 1, 1);
    private static final RawFilePatch FILE_B =
            new RawFilePatch("src/B.java", "src/OldB.java", ChangeType.RENAME, "@@ -1 +1 @@\n-x\n+y\n", 1, 1);

    @Test
    void patchShaIsIndependentOfFileOrder() {
        String first = ContextIds.patchSha(List.of(FILE_A, FILE_B));
        String shuffled = ContextIds.patchSha(List.of(FILE_B, FILE_A));
        assertThat(first).isEqualTo(shuffled);
    }

    @Test
    void patchShaChangesWithPatchContent() {
        RawFilePatch modified =
                new RawFilePatch("src/A.java", null, ChangeType.MODIFY, "@@ -1 +1 @@\n-a\n+c\n", 1, 1);
        assertThat(ContextIds.patchSha(List.of(modified)))
                .isNotEqualTo(ContextIds.patchSha(List.of(FILE_A)));
    }

    @Test
    void corpusKeyIsStableAndSensitiveToRevision() {
        RevisionSpec revision = new RevisionSpec("o/r", 1, "base", "mb", "head", "psha");

        assertThat(ContextIds.corpusKey(revision)).isEqualTo(ContextIds.corpusKey(revision));

        RevisionSpec movedHead = new RevisionSpec("o/r", 1, "base", "mb", "head2", "psha");
        assertThat(ContextIds.corpusKey(movedHead)).isNotEqualTo(ContextIds.corpusKey(revision));
    }

    @Test
    void contextIdSeparatesCorpusFromSelection() {
        RevisionSpec revision = new RevisionSpec("o/r", 1, "base", "mb", "head", "psha");
        String corpusKey = ContextIds.corpusKey(revision);

        String policyA = ContextIds.contextId(corpusKey, 1, "b-v1", "heuristic-v1", "cfg", "tok-v1");
        String policyB = ContextIds.contextId(corpusKey, 1, "b-v1", "heuristic-v2", "cfg", "tok-v1");
        String configChange = ContextIds.contextId(corpusKey, 1, "b-v1", "heuristic-v1", "cfg2", "tok-v1");

        assertThat(policyA).isNotEqualTo(policyB);
        assertThat(policyA).isNotEqualTo(configChange);
        assertThat(ContextIds.contextId(corpusKey, 1, "b-v1", "heuristic-v1", "cfg", "tok-v1"))
                .isEqualTo(policyA);
    }
}
