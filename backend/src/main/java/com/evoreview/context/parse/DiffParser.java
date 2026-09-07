package com.evoreview.context.parse;

import com.evoreview.context.model.Hunk;
import com.evoreview.context.model.HunkLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a per-file unified diff (the {@code patch} field of the GitHub files API)
 * into hunks with dual line cursors: REMOVED lines carry old-file numbers,
 * ADDED lines carry new-file numbers. Line numbers are what Phase 3 uses to
 * anchor review comments, so declared hunk counts are strictly validated.
 */
public final class DiffParser {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    public List<Hunk> parse(String path, String patch) {
        List<Hunk> hunks = new ArrayList<>();
        if (patch == null || patch.isEmpty()) {
            return hunks;
        }
        String[] rawLines = patch.split("\n", -1);
        int end = rawLines.length;
        if (end > 0 && rawLines[end - 1].isEmpty()) {
            end--;
        }

        int i = 0;
        while (i < end && !stripCr(rawLines[i]).startsWith("@@")) {
            i++;
        }
        while (i < end) {
            Matcher header = HUNK_HEADER.matcher(stripCr(rawLines[i]));
            if (!header.matches()) {
                throw new DiffParseException(path, i, "expected hunk header, got: " + abbreviate(rawLines[i]));
            }
            int oldStart = Integer.parseInt(header.group(1));
            int oldLines = header.group(2) == null ? 1 : Integer.parseInt(header.group(2));
            int newStart = Integer.parseInt(header.group(3));
            int newLines = header.group(4) == null ? 1 : Integer.parseInt(header.group(4));
            i++;

            List<HunkLine> lines = new ArrayList<>();
            int oldCursor = oldStart;
            int newCursor = newStart;
            while (i < end && !stripCr(rawLines[i]).startsWith("@@")) {
                String line = stripCr(rawLines[i]);
                if (line.startsWith("\\")) {
                    i++;
                    continue;
                }
                char prefix = line.isEmpty() ? ' ' : line.charAt(0);
                String content = line.isEmpty() ? "" : line.substring(1);
                switch (prefix) {
                    case ' ' -> lines.add(HunkLine.context(oldCursor++, newCursor++, content));
                    case '+' -> lines.add(HunkLine.added(newCursor++, content));
                    case '-' -> lines.add(HunkLine.removed(oldCursor++, content));
                    default -> throw new DiffParseException(path, i, "unexpected line prefix: " + abbreviate(line));
                }
                i++;
            }

            int consumedOld = oldCursor - oldStart;
            int consumedNew = newCursor - newStart;
            if (consumedOld != oldLines || consumedNew != newLines) {
                throw new DiffParseException(path, i,
                        "hunk line count mismatch: declared -" + oldLines + " +" + newLines
                                + " but consumed -" + consumedOld + " +" + consumedNew);
            }
            hunks.add(new Hunk(oldStart, oldLines, newStart, newLines, lines));
        }
        return hunks;
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static String abbreviate(String line) {
        return line.length() <= 80 ? line : line.substring(0, 80) + "...";
    }
}
