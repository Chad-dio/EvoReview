package com.evoreview.review;

/**
 * Rewrites a rendered diff hunk so every line carries its new-file line number,
 * letting the LLM cite exact lines in its findings. Removed lines get no number
 * (they do not exist in the new file). Example:
 *
 * <pre>
 * @@ -3,4 +3,5 @@
 *    3 | public class A {
 * -      |         int a = 1;
 * +    4 |         int b = 2;
 * </pre>
 */
public final class DiffLineAnnotator {

    private DiffLineAnnotator() {}

    public static String annotate(String hunkContent) {
        String[] lines = hunkContent.split("\n", -1);
        StringBuilder out = new StringBuilder(hunkContent.length() + 64);
        int nextNewLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("@@")) {
                nextNewLine = parseNewStart(line);
                out.append(line);
            } else if (nextNewLine < 0 || line.startsWith("\\")) {
                out.append(line);
            } else {
                char marker = line.isEmpty() ? ' ' : line.charAt(0);
                String rest = line.isEmpty() ? "" : line.substring(1);
                if (marker == '-') {
                    out.append("-      | ").append(rest);
                } else if (marker == '+') {
                    out.append(String.format("+ %4d | %s", nextNewLine, rest));
                    nextNewLine++;
                } else {
                    out.append(String.format("  %4d | %s", nextNewLine, rest));
                    nextNewLine++;
                }
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static int parseNewStart(String header) {
        int plus = header.indexOf('+');
        if (plus < 0) {
            return -1;
        }
        int i = plus + 1;
        int start = 0;
        boolean any = false;
        while (i < header.length() && Character.isDigit(header.charAt(i))) {
            start = start * 10 + (header.charAt(i) - '0');
            i++;
            any = true;
        }
        return any ? start : -1;
    }
}
