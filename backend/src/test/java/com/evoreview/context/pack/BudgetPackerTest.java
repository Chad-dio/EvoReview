package com.evoreview.context.pack;

import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.RecallSource;
import com.evoreview.context.model.RevisionSide;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetPackerTest {

    private final BudgetPacker packer = new BudgetPacker();

    @Test
    void requiredItemsAreAlwaysIncludedEvenOverBudget() {
        ContextItem required = item("REQ#1", "a/F.java", 100, true, List.of(), 1.0);

        PackResult result = packer.pack(List.of(required), 10, this::value);

        assertThat(result.included()).containsExactly(required);
        assertThat(result.estimatedTokens()).isEqualTo(100);
    }

    @Test
    void optionalItemPullsItsDependencyClosure() {
        ContextItem dependency = item("CLASS#F", "a/F.java", 10, false, List.of(), 0.1);
        ContextItem method = item("METHOD#F#m", "a/F.java", 10, false, List.of("CLASS#F"), 5.0);

        PackResult result = packer.pack(List.of(method, dependency), 20, this::value);

        assertThat(ids(result)).containsExactlyInAnyOrder("CLASS#F", "METHOD#F#m");
        assertThat(result.estimatedTokens()).isEqualTo(20);
    }

    @Test
    void dropsWhatDoesNotFit() {
        ContextItem required = item("REQ#1", "a/F.java", 80, true, List.of(), 1.0);
        ContextItem big = item("OPT#big", "a/G.java", 50, false, List.of(), 10.0);

        PackResult result = packer.pack(List.of(required, big), 100, this::value);

        assertThat(ids(result)).containsExactly("REQ#1");
        assertThat(result.dropped()).singleElement().satisfies(dropped -> {
            assertThat(dropped.itemId()).isEqualTo("OPT#big");
            assertThat(dropped.dropReason()).isEqualTo("over-budget");
        });
    }

    @Test
    void greedyPrefersHigherValueDensity() {
        ContextItem low = item("OPT#low", "a/A.java", 10, false, List.of(), 5.0);
        ContextItem high = item("OPT#high", "a/B.java", 10, false, List.of(), 9.0);

        PackResult result = packer.pack(List.of(low, high), 10, this::value);

        assertThat(ids(result)).containsExactly("OPT#high");
    }

    @Test
    void packingIsDeterministicUnderInputShuffle() {
        List<ContextItem> candidates = new ArrayList<>(List.of(
                item("REQ#1", "a/F.java", 20, true, List.of(), 1.0),
                item("OPT#1", "a/A.java", 10, false, List.of(), 5.0),
                item("OPT#2", "a/B.java", 10, false, List.of(), 5.0),
                item("OPT#3", "a/C.java", 10, false, List.of(), 5.0)));
        List<ContextItem> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);

        PackResult first = packer.pack(candidates, 40, this::value);
        PackResult second = packer.pack(shuffled, 40, this::value);

        assertThat(ids(first)).isEqualTo(ids(second));
        assertThat(first.dropped()).isEqualTo(second.dropped());
    }

    private double value(ContextItem item) {
        return item.features().getOrDefault("v", 0.0);
    }

    private static List<String> ids(PackResult result) {
        return result.included().stream().map(ContextItem::itemId).toList();
    }

    private static ContextItem item(
            String id, String path, int tokens, boolean required, List<String> requires, double value) {
        return new ContextItem(id, ItemKind.RELATED_SNIPPET,
                ContextRef.lines(RevisionSide.HEAD, path, 1, 1), "content", tokens, required,
                requires, List.of(RecallSource.REQUIRED), Map.of("v", value));
    }
}
