package com.evoreview.context;

import com.evoreview.context.model.ContextSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileContextStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndFindsSnapshot() {
        FileContextStore store = newStore();
        ContextSnapshot snapshot = new ContextSnapshot(TestSnapshots.samplePlan("ctx-1", Map.of()),
                Map.of("parseErrors", "0"));

        store.save(snapshot);

        Optional<ContextSnapshot> found = store.find("ctx-1");
        assertThat(found).contains(snapshot);
    }

    @Test
    void findReturnsEmptyForUnknownId() {
        assertThat(newStore().find("missing")).isEmpty();
    }

    @Test
    void savingSameSnapshotTwiceYieldsIdenticalBytes() throws Exception {
        FileContextStore store = newStore();
        ContextSnapshot snapshot = new ContextSnapshot(TestSnapshots.samplePlan("ctx-1", Map.of()),
                Map.of());

        store.save(snapshot);
        byte[] first = Files.readAllBytes(tempDir.resolve("ctx-1.json"));
        store.save(snapshot);
        byte[] second = Files.readAllBytes(tempDir.resolve("ctx-1.json"));

        assertThat(first).isEqualTo(second);
    }

    private FileContextStore newStore() {
        ContextProperties properties = new ContextProperties();
        properties.setStoreDir(tempDir.toString());
        return new FileContextStore(properties);
    }
}
