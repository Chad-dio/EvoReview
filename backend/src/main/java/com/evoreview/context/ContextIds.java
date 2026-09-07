package com.evoreview.context;

import com.evoreview.context.acquisition.RawFilePatch;
import com.evoreview.context.model.RevisionSpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Two-layer identity. CorpusKey pins the code world (revision + patch + acquisition
 * algorithm); ContextId pins the selection (corpus + schema + builder + policy +
 * config + tokenizer). Same CorpusKey with different policies replays cleanly.
 */
public final class ContextIds {

    public static final String ACQUISITION_VERSION = "acq-v1";
    public static final String BUILDER_VERSION = "builder-v1";

    private ContextIds() {
    }

    public static String patchSha(List<RawFilePatch> files) {
        StringBuilder input = new StringBuilder();
        files.stream()
                .sorted(Comparator.comparing(RawFilePatch::path))
                .forEach(file -> input
                        .append(file.path()).append('\0')
                        .append(file.previousPath() == null ? "" : file.previousPath()).append('\0')
                        .append(file.changeType()).append('\0')
                        .append(file.patch() == null ? "" : file.patch()).append('\0'));
        return sha256Hex(input.toString());
    }

    public static String corpusKey(RevisionSpec revision) {
        return sha256Hex(String.join("\n",
                revision.repoId(),
                revision.mergeBaseSha(),
                revision.headSha(),
                revision.patchSha(),
                ACQUISITION_VERSION));
    }

    public static String contextId(
            String corpusKey,
            int schemaVersion,
            String builderVersion,
            String policyVersion,
            String configFingerprint,
            String tokenEstimatorVersion
    ) {
        return sha256Hex(String.join("\n",
                corpusKey,
                String.valueOf(schemaVersion),
                builderVersion,
                policyVersion,
                configFingerprint,
                tokenEstimatorVersion));
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
