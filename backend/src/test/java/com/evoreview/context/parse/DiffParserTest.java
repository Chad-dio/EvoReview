package com.evoreview.context.parse;

import com.evoreview.context.model.Hunk;
import com.evoreview.context.model.HunkLine;
import com.evoreview.context.model.LineType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiffParserTest {

    private final DiffParser parser = new DiffParser();

    private static final String SIMPLE_OLD = """
            line1
            line2
            line3
            line4
            """;

    private static final String SIMPLE_NEW = """
            line1
            line2 changed
            line3
            line4
            line5 added
            """;

    private static final String SIMPLE_PATCH = """
            @@ -1,4 +1,5 @@
             line1
            -line2
            +line2 changed
             line3
             line4
            +line5 added
            """;

    private static final String MULTI_OLD = """
            a
            b
            c
            d
            e
            f
            g
            """;

    private static final String MULTI_NEW = """
            a
            B
            c
            d
            e
            f
            G
            """;

    private static final String MULTI_PATCH = """
            @@ -2 +2 @@
            -b
            +B
            @@ -7 +7 @@
            -g
            +G
            """;

    @Test
    void parsesSimpleModificationWithDualLineNumbers() {
        List<Hunk> hunks = parser.parse("src/A.java", SIMPLE_PATCH);

        assertThat(hunks).hasSize(1);
        Hunk hunk = hunks.get(0);
        assertThat(hunk.oldStart()).isEqualTo(1);
        assertThat(hunk.oldLines()).isEqualTo(4);
        assertThat(hunk.newStart()).isEqualTo(1);
        assertThat(hunk.newLines()).isEqualTo(5);
        assertThat(hunk.lines()).containsExactly(
                HunkLine.context(1, 1, "line1"),
                HunkLine.removed(2, "line2"),
                HunkLine.added(2, "line2 changed"),
                HunkLine.context(3, 3, "line3"),
                HunkLine.context(4, 4, "line4"),
                HunkLine.added(5, "line5 added")
        );
    }

    @Test
    void anchorsHoldForSimpleModification() {
        assertAnchors(SIMPLE_OLD, SIMPLE_NEW, SIMPLE_PATCH);
    }

    @Test
    void fullRoundtripReconstructsNewFileWhenHunksCoverEverything() {
        assertFullRoundtrip(SIMPLE_OLD, SIMPLE_NEW, SIMPLE_PATCH);
    }

    @Test
    void parsesMultipleHunks() {
        List<Hunk> hunks = parser.parse("f.txt", MULTI_PATCH);

        assertThat(hunks).hasSize(2);
        assertThat(hunks.get(0).oldStart()).isEqualTo(2);
        assertThat(hunks.get(0).lines()).containsExactly(
                HunkLine.removed(2, "b"), HunkLine.added(2, "B"));
        assertThat(hunks.get(1).oldStart()).isEqualTo(7);
        assertThat(hunks.get(1).lines()).containsExactly(
                HunkLine.removed(7, "g"), HunkLine.added(7, "G"));

        assertAnchors(MULTI_OLD, MULTI_NEW, MULTI_PATCH);
    }

    @Test
    void parsesNewFile() {
        String patch = """
                @@ -0,0 +1,3 @@
                +package x;
                +
                +class A {}
                """;

        List<Hunk> hunks = parser.parse("src/A.java", patch);

        assertThat(hunks).hasSize(1);
        assertThat(hunks.get(0).lines()).allMatch(line -> line.type() == LineType.ADDED);
        assertFullRoundtrip("", "package x;\n\nclass A {}\n", patch);
    }

    @Test
    void parsesDeletedFile() {
        String patch = """
                @@ -1,2 +0,0 @@
                -package x;
                -class A {}
                """;

        List<Hunk> hunks = parser.parse("src/A.java", patch);

        assertThat(hunks).hasSize(1);
        assertThat(hunks.get(0).lines()).allMatch(line -> line.type() == LineType.REMOVED);
        assertFullRoundtrip("package x;\nclass A {}\n", "", patch);
    }

    @Test
    void skipsNoNewlineMarkerWithoutMovingCursors() {
        String patch = """
                @@ -1,2 +1,2 @@
                 line1
                -line2
                \\ No newline at end of file
                +line2 new
                \\ No newline at end of file
                """;

        List<Hunk> hunks = parser.parse("f.txt", patch);

        assertThat(hunks).hasSize(1);
        assertThat(hunks.get(0).lines()).containsExactly(
                HunkLine.context(1, 1, "line1"),
                HunkLine.removed(2, "line2"),
                HunkLine.added(2, "line2 new")
        );
    }

    @Test
    void stripsCarriageReturnsFromCrlfPatches() {
        String patch = "@@ -1 +1 @@\r\n-old line\r\n+new line\r\n";

        List<Hunk> hunks = parser.parse("f.txt", patch);

        assertThat(hunks.get(0).lines()).containsExactly(
                HunkLine.removed(1, "old line"),
                HunkLine.added(1, "new line")
        );
    }

    @Test
    void skipsFileHeaderLinesBeforeFirstHunk() {
        String patch = """
                index abc..def 100644
                --- a/f.txt
                +++ b/f.txt
                @@ -1 +1 @@
                -a
                +b
                """;

        List<Hunk> hunks = parser.parse("f.txt", patch);

        assertThat(hunks).hasSize(1);
    }

    @Test
    void rejectsHunkWhoseLineCountsDoNotMatch() {
        String patch = """
                @@ -1,2 +1,1 @@
                -a
                +b
                """;

        assertThatThrownBy(() -> parser.parse("f.txt", patch))
                .isInstanceOf(DiffParseException.class)
                .hasMessageContaining("count mismatch");
    }

    @Test
    void rejectsGarbageInsideHunk() {
        String patch = """
                @@ -1 +1 @@
                ?not-a-diff-line
                """;

        assertThatThrownBy(() -> parser.parse("f.txt", patch))
                .isInstanceOf(DiffParseException.class);
    }

    @Test
    void returnsEmptyForMissingPatch() {
        assertThat(parser.parse("big.bin", null)).isEmpty();
        assertThat(parser.parse("big.bin", "")).isEmpty();
    }

    /**
     * The invariants Phase 3 anchoring depends on: every ADDED line's content equals
     * the new file at its newLineNo; every REMOVED/CONTEXT line equals the old file
     * at its oldLineNo; CONTEXT lines additionally match the new file at newLineNo.
     */
    private void assertAnchors(String oldContent, String newContent, String patch) {
        List<String> oldLines = splitLines(oldContent);
        List<String> newLines = splitLines(newContent);
        for (Hunk hunk : parser.parse("f", patch)) {
            for (HunkLine line : hunk.lines()) {
                switch (line.type()) {
                    case CONTEXT -> {
                        assertThat(oldLines.get(line.oldLineNo() - 1))
                                .as("context line %d anchors to old file", line.oldLineNo())
                                .isEqualTo(line.content());
                        assertThat(newLines.get(line.newLineNo() - 1))
                                .as("context line %d anchors to new file", line.newLineNo())
                                .isEqualTo(line.content());
                    }
                    case REMOVED -> assertThat(oldLines.get(line.oldLineNo() - 1))
                            .as("removed line %d anchors to old file", line.oldLineNo())
                            .isEqualTo(line.content());
                    case ADDED -> assertThat(newLines.get(line.newLineNo() - 1))
                            .as("added line %d anchors to new file", line.newLineNo())
                            .isEqualTo(line.content());
                }
            }
        }
    }

    /**
     * Old file + parsed hunks must reconstruct the new file exactly. Only valid when
     * the hunks cover the whole file (full-context, new-file, or deleted-file patches).
     */
    private void assertFullRoundtrip(String oldContent, String newContent, String patch) {
        List<String> oldLines = splitLines(oldContent);
        List<String> rebuilt = new ArrayList<>();
        for (Hunk hunk : parser.parse("f", patch)) {
            for (HunkLine line : hunk.lines()) {
                switch (line.type()) {
                    case CONTEXT -> {
                        assertThat(oldLines.get(line.oldLineNo() - 1)).isEqualTo(line.content());
                        rebuilt.add(line.content());
                    }
                    case REMOVED -> assertThat(oldLines.get(line.oldLineNo() - 1))
                            .isEqualTo(line.content());
                    case ADDED -> rebuilt.add(line.content());
                }
            }
        }
        assertThat(rebuilt).containsExactlyElementsOf(splitLines(newContent));
    }

    private static List<String> splitLines(String content) {
        if (content.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
        if (lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }
}
