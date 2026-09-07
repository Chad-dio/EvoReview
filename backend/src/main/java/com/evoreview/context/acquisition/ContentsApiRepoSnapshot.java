package com.evoreview.context.acquisition;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTreeEntry;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Fallback snapshot for repositories too large to archive: only requested files
 * are fetched through the contents API; the listing comes from the git tree API.
 */
public final class ContentsApiRepoSnapshot implements RepoSnapshot {

    private final GHRepository repository;
    private final String ref;

    public ContentsApiRepoSnapshot(GHRepository repository, String ref) {
        this.repository = repository;
        this.ref = ref;
    }

    @Override
    public Kind kind() {
        return Kind.CONTENTS_LAZY;
    }

    @Override
    public List<String> listFiles() throws IOException {
        return repository.getTreeRecursive(ref, 1).getTree().stream()
                .filter(entry -> "blob".equals(entry.getType()))
                .map(GHTreeEntry::getPath)
                .sorted()
                .toList();
    }

    @Override
    public Optional<String> readFile(String path) throws IOException {
        try {
            return Optional.of(repository.getFileContent(path, ref).getContent());
        } catch (GHFileNotFoundException ex) {
            return Optional.empty();
        }
    }
}
