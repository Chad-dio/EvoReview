package com.evoreview.context.rank;

import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.RecallSource;
import com.evoreview.context.model.RevisionSide;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HeuristicContextPolicyTest {

    private final HeuristicContextPolicy policy = new HeuristicContextPolicy();

    @Test
    void scoresKindWeightTimesConfidence() {
        assertThat(policy.score(item(ItemKind.OVERRIDE, 0.9))).isCloseTo(0.81, within(1e-9));
        assertThat(policy.score(item(ItemKind.CALLER, 0.85))).isCloseTo(0.68, within(1e-9));
        assertThat(policy.score(item(ItemKind.TEST, 1.0))).isCloseTo(0.85, within(1e-9));
    }

    @Test
    void missingConfidenceDefaultsToHalf() {
        ContextItem noConfidence = new ContextItem("id", ItemKind.CALLER,
                ContextRef.file(RevisionSide.HEAD, "a/B.java"), "x", 10, false,
                List.of(), List.of(RecallSource.SYMBOL_GRAPH), Map.of());

        assertThat(policy.score(noConfidence)).isCloseTo(0.4, within(1e-9));
    }

    @Test
    void hasStableVersion() {
        assertThat(policy.version()).isEqualTo("heuristic-v1");
    }

    private static ContextItem item(ItemKind kind, double confidence) {
        return new ContextItem("id", kind, ContextRef.file(RevisionSide.HEAD, "a/B.java"),
                "x", 10, false, List.of(), List.of(RecallSource.SYMBOL_GRAPH),
                Map.of("confidence", confidence));
    }
}
