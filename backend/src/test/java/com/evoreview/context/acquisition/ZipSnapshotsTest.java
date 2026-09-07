package com.evoreview.context.acquisition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipSnapshotsTest {

    @TempDir
    Path targetDir;

    @Test
    void extractsArchiveAndStripsTopLevelPrefix() throws IOException {
        byte[] zip = zipOf(Map.of(
                "o-r-abc123/README.md", "hi",
                "o-r-abc123/src/A.java", "class A {}"));

        ZipSnapshots.extract(new ByteArrayInputStream(zip), targetDir, 1024, 1024 * 1024);

        assertThat(Files.readString(targetDir.resolve("README.md"))).isEqualTo("hi");
        assertThat(Files.readString(targetDir.resolve("src/A.java"))).isEqualTo("class A {}");
    }

    @Test
    void rejectsZipSlipEntries() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("o-r-abc123/ok.txt", "ok");
        entries.put("o-r-abc123/../../evil.txt", "evil");
        byte[] zip = zipOf(entries);

        assertThatThrownBy(() ->
                ZipSnapshots.extract(new ByteArrayInputStream(zip), targetDir, 1024, 1024 * 1024))
                .isInstanceOf(SnapshotGuardException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void rejectsOversizedSingleFile() throws IOException {
        byte[] zip = zipOf(Map.of("o-r-abc123/big.txt", "0123456789"));

        assertThatThrownBy(() ->
                ZipSnapshots.extract(new ByteArrayInputStream(zip), targetDir, 4, 1024 * 1024))
                .isInstanceOf(SnapshotGuardException.class)
                .hasMessageContaining("per-file limit");
    }

    @Test
    void rejectsOversizedTotal() throws IOException {
        byte[] zip = zipOf(Map.of(
                "o-r-abc123/a.txt", "0123456789",
                "o-r-abc123/b.txt", "0123456789"));

        assertThatThrownBy(() ->
                ZipSnapshots.extract(new ByteArrayInputStream(zip), targetDir, 1024, 12))
                .isInstanceOf(SnapshotGuardException.class)
                .hasMessageContaining("total size limit");
    }

    static byte[] zipOf(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
