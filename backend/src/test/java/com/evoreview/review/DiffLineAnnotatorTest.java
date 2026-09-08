package com.evoreview.review;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiffLineAnnotatorTest {

    @Test
    void annotatesContextAndAddedLinesWithNewFileNumbers() {
        String hunk = "@@ -3,4 +3,5 @@\n"
                + " public class A {\n"
                + "     public void m() {\n"
                + "-        int a = 1;\n"
                + "+        int b = 2;\n"
                + "     }";

        String[] lines = DiffLineAnnotator.annotate(hunk).split("\n");

        assertEquals("@@ -3,4 +3,5 @@", lines[0]);
        assertEquals("     3 | public class A {", lines[1]);
        assertEquals("     4 |     public void m() {", lines[2]);
        assertEquals("-      |         int a = 1;", lines[3]);
        assertEquals("+    5 |         int b = 2;", lines[4]);
        assertEquals("     6 |     }", lines[5]);
    }

    @Test
    void keepsHeaderTailAndNoNewlineMarker() {
        String hunk = "@@ -1,2 +1,2 @@ class X {\n"
                + "-int a;\n"
                + "+int b;\n"
                + "\\ No newline at end of file";

        String[] lines = DiffLineAnnotator.annotate(hunk).split("\n");

        assertEquals("@@ -1,2 +1,2 @@ class X {", lines[0]);
        assertEquals("-      | int a;", lines[1]);
        assertEquals("+    1 | int b;", lines[2]);
        assertEquals("\\ No newline at end of file", lines[3]);
    }

    @Test
    void contentWithoutHeaderPassesThroughUnchanged() {
        String content = "plain text\nsecond line";
        assertEquals(content, DiffLineAnnotator.annotate(content));
    }
}
