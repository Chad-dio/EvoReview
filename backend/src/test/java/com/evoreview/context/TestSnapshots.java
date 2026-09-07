package com.evoreview.context;

import com.evoreview.context.model.Capability;
import com.evoreview.context.model.CapabilityReport;
import com.evoreview.context.model.ChannelState;
import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.GlobalChangeSummary;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.RecallSource;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.context.model.ReviewSlice;
import com.evoreview.context.model.RevisionSide;
import com.evoreview.context.model.RevisionSpec;

import java.util.List;
import java.util.Map;

public final class TestSnapshots {

    private TestSnapshots() {
    }

    public static RevisionSpec sampleRevision() {
        return new RevisionSpec("octo/repo", 42, "base111", "merge00", "head999", "patchff");
    }

    public static ReviewPlan samplePlan(String contextId, Map<String, Double> features) {
        ContextItem item = new ContextItem(
                "CTX-1",
                ItemKind.DIFF_HUNK,
                ContextRef.lines(RevisionSide.HEAD, "src/A.java", 1, 5),
                "@@ -1,4 +1,5 @@",
                25,
                true,
                List.of(),
                List.of(RecallSource.REQUIRED),
                features
        );
        ReviewSlice slice = new ReviewSlice("slice-1", List.of(item), List.of(), 25, 9000);
        CapabilityReport capabilities = new CapabilityReport(
                Capability.COMPLETE, Capability.COMPLETE, Capability.PARTIAL, 0.95,
                ChannelState.ENABLED, ChannelState.DISABLED_BY_CONFIG,
                ChannelState.UNAVAILABLE_NO_HISTORY);
        return new ReviewPlan(
                ReviewPlan.CURRENT_SCHEMA_VERSION,
                contextId,
                sampleRevision(),
                "heuristic-v1",
                "config-fp",
                new GlobalChangeSummary(1, 5, 1, List.of()),
                List.of(slice),
                capabilities
        );
    }
}
