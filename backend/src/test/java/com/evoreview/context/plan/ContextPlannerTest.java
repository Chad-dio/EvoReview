package com.evoreview.context.plan;

import com.evoreview.context.CharsDivFourTokenEstimator;
import com.evoreview.context.ContextProperties;
import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.ChangedFile;
import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.EdgeType;
import com.evoreview.context.model.FileCategory;
import com.evoreview.context.model.Hunk;
import com.evoreview.context.model.HunkLine;
import com.evoreview.context.model.ItemKind;
import com.evoreview.context.model.RecallSource;
import com.evoreview.context.model.RevisionSide;
import com.evoreview.context.parse.SensitiveFileClassifier;
import com.evoreview.context.semantic.ChangedSymbol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPlannerTest {

    private static final String FOO = "src/main/java/com/x/Foo.java";
    private static final String CALLER = "src/main/java/com/y/Caller.java";

    private static final String FOO_CONTENT = """
            package com.x;

            public class Foo {
                void save() {
                }
            }
            """;

    private static final String CALLER_CONTENT = """
            package com.y;
            class Caller {
                void go() {
                }
            }
            """;

    private final ContextPlanner planner = new ContextPlanner(
            new CharsDivFourTokenEstimator(), new ContextProperties(),
            SensitiveFileClassifier.withDefaults());

    @Test
    void buildsRequiredDiffMethodAndClassHeaderItems() {
        ChangedSymbol save = new ChangedSymbol(
                FOO, RevisionSide.HEAD, "com.x.Foo", "save", "save()", 0, 4, 5);

        PlannedContext planned = planner.plan(new PlannerInput(
                List.of(changedFoo()), List.of(save), List.of(),
                Map.of(FOO, FOO_CONTENT), Map.of(), Map.of()));

        Map<String, ContextItem> byId = byId(planned);

        ContextItem diff = byId.get("DIFF#" + FOO + "#0");
        assertThat(diff).isNotNull();
        assertThat(diff.required()).isTrue();
        assertThat(diff.content()).startsWith("@@ -3,4 +3,5 @@");

        ContextItem method = byId.get("METHOD#com.x.Foo#save()");
        assertThat(method).isNotNull();
        assertThat(method.required()).isTrue();
        assertThat(method.requires()).containsExactly("CLASS#com.x.Foo");
        assertThat(method.content()).contains("void save() {");

        ContextItem classHeader = byId.get("CLASS#com.x.Foo");
        assertThat(classHeader).isNotNull();
        assertThat(classHeader.content()).contains("package com.x;", "public class Foo {");
    }

    @Test
    void buildsEdgeItemWithConfidenceAndOwner() {
        ContextEdge call = new ContextEdge(EdgeType.CALLS,
                ContextRef.lines(RevisionSide.HEAD, CALLER, 2, 4),
                new ContextRef(RevisionSide.HEAD, FOO, 4, 5, "com.x.Foo#save()", null),
                0.85, RecallSource.SYMBOL_GRAPH);

        PlannedContext planned = planner.plan(new PlannerInput(
                List.of(changedFoo()), List.of(), List.of(call),
                Map.of(FOO, FOO_CONTENT, CALLER, CALLER_CONTENT), Map.of(), Map.of()));

        ContextItem caller = byId(planned).get("CALLER#" + CALLER + "#2");
        assertThat(caller).isNotNull();
        assertThat(caller.kind()).isEqualTo(ItemKind.CALLER);
        assertThat(caller.required()).isFalse();
        assertThat(caller.features().get("confidence")).isEqualTo(0.85);
        assertThat(caller.sources()).containsExactly(RecallSource.SYMBOL_GRAPH);
        assertThat(caller.content()).contains("class Caller {", "void go() {");
        assertThat(planned.optionalItemOwners().get("CALLER#" + CALLER + "#2")).isEqualTo(FOO);
    }

    @Test
    void skipsEdgeContentFromChangedOrSensitiveFiles() {
        ContextEdge selfChanged = new ContextEdge(EdgeType.CALLS,
                ContextRef.lines(RevisionSide.HEAD, FOO, 1, 1),
                new ContextRef(RevisionSide.HEAD, FOO, 4, 5, "com.x.Foo#save()", null),
                0.9, RecallSource.SYMBOL_GRAPH);
        ContextEdge secret = new ContextEdge(EdgeType.CALLS,
                ContextRef.lines(RevisionSide.HEAD, "config/.env", 1, 1),
                new ContextRef(RevisionSide.HEAD, FOO, 4, 5, "com.x.Foo#save()", null),
                0.9, RecallSource.SYMBOL_GRAPH);

        PlannedContext planned = planner.plan(new PlannerInput(
                List.of(changedFoo()), List.of(), List.of(selfChanged, secret),
                Map.of(FOO, FOO_CONTENT, "config/.env", "PASSWORD=hunter2"), Map.of(), Map.of()));

        assertThat(byId(planned).keySet()).noneMatch(id -> id.startsWith("CALLER#"));
    }

    @Test
    void sensitiveChangedFileGetsWithheldMarker() {
        ChangedFile env = new ChangedFile(".env", null, ChangeType.MODIFY, FileCategory.CONFIG,
                List.of(new Hunk(1, 1, 1, 1, List.of(HunkLine.added(1, "PASSWORD=hunter2")))));

        PlannedContext planned = planner.plan(new PlannerInput(
                List.of(env), List.of(), List.of(), Map.of(), Map.of(), Map.of()));

        ContextItem item = byId(planned).get("FILE#.env");
        assertThat(item).isNotNull();
        assertThat(item.content()).contains("withheld").doesNotContain("hunter2");
        assertThat(planned.sensitiveSkipped()).containsExactly(".env");
    }

    @Test
    void fileWithoutPatchGetsMetadataItem() {
        ChangedFile big = new ChangedFile("assets/huge.bin", null, ChangeType.MODIFY,
                FileCategory.BINARY, List.of());

        PlannedContext planned = planner.plan(new PlannerInput(
                List.of(big), List.of(), List.of(), Map.of(), Map.of(),
                Map.of("assets/huge.bin", "modify, +0/-0")));

        ContextItem item = byId(planned).get("FILE#assets/huge.bin");
        assertThat(item).isNotNull();
        assertThat(item.required()).isTrue();
        assertThat(item.content()).contains("modify, +0/-0");
    }

    @Test
    void planningIsDeterministic() {
        ChangedSymbol save = new ChangedSymbol(
                FOO, RevisionSide.HEAD, "com.x.Foo", "save", "save()", 0, 4, 5);
        PlannerInput input = new PlannerInput(
                List.of(changedFoo()), List.of(save), List.of(),
                Map.of(FOO, FOO_CONTENT), Map.of(), Map.of());

        assertThat(planner.plan(input).items()).isEqualTo(planner.plan(input).items());
    }

    private static ChangedFile changedFoo() {
        return new ChangedFile(FOO, null, ChangeType.MODIFY, FileCategory.SOURCE, List.of(
                new Hunk(3, 4, 3, 5, List.of(
                        HunkLine.context(3, 3, "public class Foo {"),
                        HunkLine.added(4, "    void save() {"),
                        HunkLine.context(4, 5, "    }"),
                        HunkLine.context(5, 6, "}")
                ))));
    }

    private static Map<String, ContextItem> byId(PlannedContext planned) {
        return planned.items().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ContextItem::itemId, item -> item, (a, b) -> a, java.util.LinkedHashMap::new));
    }
}
