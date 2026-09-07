package com.evoreview.context.acquisition;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Guarded zip extraction for repository archives. Defends against zip-slip,
 * per-file bombs, and total-size bombs, and strips the single top-level
 * "{owner}-{repo}-{sha}/" prefix that GitHub archives add.
 */
public final class ZipSnapshots {

    private ZipSnapshots() {
    }

    public static void extract(InputStream archive, Path targetDir, long maxFileBytes, long maxTotalBytes)
            throws IOException {
        Files.createDirectories(targetDir);
        long totalBytes = 0;
        String prefix = null;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (prefix == null) {
                    int slash = name.indexOf('/');
                    prefix = slash < 0 ? "" : name.substring(0, slash + 1);
                }
                String relative = name.startsWith(prefix) ? name.substring(prefix.length()) : name;
                if (relative.isEmpty() || entry.isDirectory()) {
                    continue;
                }
                Path target = targetDir.resolve(relative).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new SnapshotGuardException("zip entry escapes target directory: " + name);
                }
                Files.createDirectories(target.getParent());
                totalBytes += copyBounded(zip, target, maxFileBytes, name);
                if (totalBytes > maxTotalBytes) {
                    throw new SnapshotGuardException(
                            "snapshot exceeds total size limit of " + maxTotalBytes + " bytes");
                }
            }
        }
    }

    private static long copyBounded(InputStream in, Path target, long maxFileBytes, String entryName)
            throws IOException {
        long written = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > maxFileBytes) {
                    throw new SnapshotGuardException(
                            "zip entry exceeds per-file limit of " + maxFileBytes + " bytes: " + entryName);
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }
}
