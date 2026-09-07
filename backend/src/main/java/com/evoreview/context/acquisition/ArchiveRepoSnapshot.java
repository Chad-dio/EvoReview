package com.evoreview.context.acquisition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A fully materialized snapshot on local disk (extracted zipball), keyed by head SHA.
 * listFiles is sorted so downstream builders never observe filesystem order.
 */
public final class ArchiveRepoSnapshot implements RepoSnapshot {

    private final Path root;

    public ArchiveRepoSnapshot(Path root) {
        this.root = root;
    }

    @Override
    public Kind kind() {
        return Kind.ARCHIVE_FULL;
    }

    @Override
    public List<String> listFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(p -> p.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    @Override
    public Optional<String> readFile(String path) throws IOException {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    }

    public Path root() {
        return root;
    }
}
