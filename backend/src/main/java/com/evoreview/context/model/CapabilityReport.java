package com.evoreview.context.model;

import java.util.Objects;

public record CapabilityReport(
        Capability patch,
        Capability headSnapshot,
        Capability mergeBaseFiles,
        double astCoverage,
        ChannelState symbolRecall,
        ChannelState lexicalRecall,
        ChannelState coChange
) {
    public CapabilityReport {
        Objects.requireNonNull(patch, "patch");
        Objects.requireNonNull(headSnapshot, "headSnapshot");
        Objects.requireNonNull(mergeBaseFiles, "mergeBaseFiles");
        Objects.requireNonNull(symbolRecall, "symbolRecall");
        Objects.requireNonNull(lexicalRecall, "lexicalRecall");
        Objects.requireNonNull(coChange, "coChange");
    }
}
