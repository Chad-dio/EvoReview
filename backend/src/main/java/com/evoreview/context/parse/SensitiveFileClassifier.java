package com.evoreview.context.parse;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Locale;

/**
 * Keeps secrets out of LLM context even when a recall channel would match them.
 * Patterns are globs matched against the lower-cased file basename.
 */
public class SensitiveFileClassifier {

    public static final List<String> DEFAULT_PATTERNS = List.of(
            ".env*", "*.pem", "*.key", "*.p12", "*.jks", "credentials*", "secrets*");

    private final List<PathMatcher> matchers;

    public SensitiveFileClassifier(List<String> patterns) {
        this.matchers = (patterns == null ? DEFAULT_PATTERNS : patterns).stream()
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .toList();
    }

    public static SensitiveFileClassifier withDefaults() {
        return new SensitiveFileClassifier(DEFAULT_PATTERNS);
    }

    public boolean isSensitive(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String name = path.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        name = (slash < 0 ? name : name.substring(slash + 1)).toLowerCase(Locale.ROOT);
        Path fileName = Path.of(name);
        return matchers.stream().anyMatch(matcher -> matcher.matches(fileName));
    }
}
