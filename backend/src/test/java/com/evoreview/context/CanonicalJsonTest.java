package com.evoreview.context;

import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.ContextSnapshot;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.context.model.RevisionSide;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalJsonTest {

    @Test
    void repeatedSerializationIsByteIdentical() {
        ReviewPlan plan = TestSnapshots.samplePlan("ctx-1", Map.of("a", 1.0, "b", 2.0));
        String first = CanonicalJson.write(new ContextSnapshot(plan, Map.of()));
        String second = CanonicalJson.write(new ContextSnapshot(plan, Map.of()));
        assertThat(first).isEqualTo(second);
    }

    @Test
    void mapInsertionOrderDoesNotChangeOutput() {
        Map<String, String> forward = new LinkedHashMap<>();
        forward.put("alpha", "1");
        forward.put("beta", "2");
        forward.put("gamma", "3");
        Map<String, String> shuffled = new LinkedHashMap<>();
        shuffled.put("gamma", "3");
        shuffled.put("alpha", "1");
        shuffled.put("beta", "2");

        ReviewPlan plan = TestSnapshots.samplePlan("ctx-1", Map.of());
        String first = CanonicalJson.write(new ContextSnapshot(plan, forward));
        String second = CanonicalJson.write(new ContextSnapshot(plan, shuffled));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void nullFieldsAreOmittedFromOutput() {
        ContextRef ref = ContextRef.file(RevisionSide.HEAD, "src/A.java");
        String json = CanonicalJson.write(ref);
        assertThat(json).doesNotContain("startLine");
        assertThat(json).doesNotContain("symbolId");
        assertThat(json).contains("\"path\":\"src/A.java\"");
    }

    @Test
    void roundtripsThroughBytes() {
        ContextSnapshot original =
                new ContextSnapshot(TestSnapshots.samplePlan("ctx-1", Map.of("k", 1.5)),
                        Map.of("parseErrors", "0"));
        ContextSnapshot restored = CanonicalJson.read(CanonicalJson.writeBytes(original), ContextSnapshot.class);
        assertThat(restored).isEqualTo(original);
    }
}
