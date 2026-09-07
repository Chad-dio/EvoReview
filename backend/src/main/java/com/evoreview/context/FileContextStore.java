package com.evoreview.context;

import com.evoreview.context.model.ContextSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * File-system ContextStore: one JSON file per contextId under store-dir.
 * Swappable for a database in Phase 4 without touching callers.
 */
@Component
public class FileContextStore implements ContextStore {

    private static final Logger log = LoggerFactory.getLogger(FileContextStore.class);

    private final ContextProperties properties;

    public FileContextStore(ContextProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<ContextSnapshot> find(String contextId) {
        Path file = fileFor(contextId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(CanonicalJson.read(Files.readAllBytes(file), ContextSnapshot.class));
        } catch (IOException | UncheckedIOException ex) {
            log.warn("Failed to read context snapshot {}", file, ex);
            return Optional.empty();
        }
    }

    @Override
    public void save(ContextSnapshot snapshot) {
        Path file = fileFor(snapshot.plan().contextId());
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, CanonicalJson.writeBytes(snapshot));
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to persist context snapshot " + file, ex);
        }
    }

    private Path fileFor(String contextId) {
        return properties.resolvedStoreDir().resolve(contextId + ".json");
    }
}
