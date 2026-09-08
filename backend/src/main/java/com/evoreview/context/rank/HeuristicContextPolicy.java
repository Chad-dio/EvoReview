package com.evoreview.context.rank;

import com.evoreview.context.model.ContextItem;
import org.springframework.stereotype.Component;

/**
 * heuristic-v1: kind weight × edge confidence. Hand-tuned baseline; learned
 * policies replace this once attribution data exists (and must beat it in the
 * harness to be promoted).
 */
@Component
public class HeuristicContextPolicy implements ContextPolicy {

    public static final String VERSION = "heuristic-v1";

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public double score(ContextItem candidate) {
        double confidence = candidate.features().getOrDefault("confidence", 0.5);
        return kindWeight(candidate) * confidence;
    }

    private static double kindWeight(ContextItem candidate) {
        return switch (candidate.kind()) {
            case OVERRIDE -> 0.9;
            case TEST -> 0.85;
            case CALLER -> 0.8;
            case RELATED_FILE, RELATED_SNIPPET -> 0.5;
            case METADATA -> 0.2;
            case DIFF_HUNK, CHANGED_METHOD -> 1.0;
        };
    }
}
