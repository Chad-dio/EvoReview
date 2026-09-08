package com.evoreview.context;

import com.evoreview.context.acquisition.PatchAcquisition;
import com.evoreview.context.acquisition.PatchProvider;
import com.evoreview.context.acquisition.RawFilePatch;
import com.evoreview.context.acquisition.RepoSnapshot;
import com.evoreview.context.acquisition.RepoSnapshotProvider;
import com.evoreview.context.model.Capability;
import com.evoreview.context.model.CapabilityReport;
import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.ChangedFile;
import com.evoreview.context.model.ChannelState;
import com.evoreview.context.model.ContextBuildResult;
import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.ContextSnapshot;
import com.evoreview.context.model.FileCategory;
import com.evoreview.context.model.GlobalChangeSummary;
import com.evoreview.context.model.Hunk;
import com.evoreview.context.model.ReviewPlan;
import com.evoreview.context.model.ReviewSlice;
import com.evoreview.context.model.RevisionSide;
import com.evoreview.context.model.RevisionSpec;
import com.evoreview.context.pack.BudgetPacker;
import com.evoreview.context.pack.PackResult;
import com.evoreview.context.pack.ReviewSlicer;
import com.evoreview.context.pack.SliceAssignment;
import com.evoreview.context.parse.ChangeClassifier;
import com.evoreview.context.parse.DiffParseException;
import com.evoreview.context.parse.DiffParser;
import com.evoreview.context.plan.ContextPlanner;
import com.evoreview.context.plan.PlannerInput;
import com.evoreview.context.plan.PlannedContext;
import com.evoreview.context.rank.ContextPolicy;
import com.evoreview.context.recall.RecallChannel;
import com.evoreview.context.recall.RecallInput;
import com.evoreview.context.semantic.AstProvider;
import com.evoreview.context.semantic.ChangedSymbol;
import com.evoreview.context.semantic.ChangedSymbolLocator;
import com.evoreview.context.semantic.DeclarationIndex;
import com.evoreview.context.semantic.InvocationIndex;
import com.evoreview.context.semantic.OldSideAnalysis;
import com.evoreview.context.semantic.OldSideAnalyzer;
import com.evoreview.context.semantic.ParseResult;
import com.evoreview.context.semantic.SourceInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Orchestrates one context build: acquire → parse → semantic → recall → plan →
 * slice → pack → freeze. Pure orchestration over immutable inputs; never calls
 * GitHub itself (providers do), never posts comments (the caller does). Any
 * failure degrades or returns Skipped — the webhook thread must not die here.
 */
@Component
public class ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilder.class);

    private final PatchProvider patchProvider;
    private final RepoSnapshotProvider snapshotProvider;
    private final AstProvider astProvider;
    private final ChangedSymbolLocator symbolLocator;
    private final OldSideAnalyzer oldSideAnalyzer;
    private final List<RecallChannel> recallChannels;
    private final ContextPlanner planner;
    private final ContextStore contextStore;
    private final ContextProperties properties;
    private final TokenEstimator tokenEstimator;
    private final ContextPolicy policy;

    private final DiffParser diffParser = new DiffParser();
    private final ChangeClassifier changeClassifier = new ChangeClassifier();
    private final BudgetPacker packer = new BudgetPacker();
    private final ReviewSlicer slicer = new ReviewSlicer();

    public ContextBuilder(
            PatchProvider patchProvider,
            RepoSnapshotProvider snapshotProvider,
            AstProvider astProvider,
            ChangedSymbolLocator symbolLocator,
            OldSideAnalyzer oldSideAnalyzer,
            List<RecallChannel> recallChannels,
            ContextPlanner planner,
            ContextStore contextStore,
            ContextProperties properties,
            TokenEstimator tokenEstimator,
            ContextPolicy policy
    ) {
        this.patchProvider = patchProvider;
        this.snapshotProvider = snapshotProvider;
        this.astProvider = astProvider;
        this.symbolLocator = symbolLocator;
        this.oldSideAnalyzer = oldSideAnalyzer;
        this.recallChannels = recallChannels;
        this.planner = planner;
        this.contextStore = contextStore;
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
        this.policy = policy;
    }

    public ContextBuildResult build(long installationId, String owner, String repo, int prNumber) {
        try {
            return doBuild(installationId, owner, repo, prNumber);
        } catch (Exception ex) {
            log.error("Context build failed for {}/{}#{}", owner, repo, prNumber, ex);
            return new ContextBuildResult.Skipped("context build failed: " + ex.getMessage());
        }
    }

    private ContextBuildResult doBuild(long installationId, String owner, String repo, int prNumber)
            throws IOException {
        PatchAcquisition acquisition = patchProvider.fetch(installationId, owner, repo, prNumber);
        RevisionSpec revision = acquisition.revision();

        String corpusKey = ContextIds.corpusKey(revision);
        String configFingerprint = ContextIds.configFingerprint(properties);
        String contextId = ContextIds.contextId(corpusKey, ReviewPlan.CURRENT_SCHEMA_VERSION,
                ContextIds.BUILDER_VERSION, policy.version(), configFingerprint, tokenEstimator.version());

        Optional<ContextSnapshot> cached = contextStore.find(contextId);
        if (cached.isPresent()) {
            log.info("Reusing frozen context {} for {}/{}#{}", contextId, owner, repo, prNumber);
            return new ContextBuildResult.Ready(cached.get().plan());
        }

        List<ChangedFile> changedFiles = new ArrayList<>();
        int missingPatches = 0;
        int parseFailures = 0;
        Map<String, String> fileStats = new HashMap<>();
        for (RawFilePatch raw : acquisition.patch().files()) {
            FileCategory category = changeClassifier.classify(raw.path(), null);
            List<Hunk> hunks = List.of();
            if (raw.patch() == null) {
                missingPatches++;
            } else {
                try {
                    hunks = diffParser.parse(raw.path(), raw.patch());
                } catch (DiffParseException ex) {
                    parseFailures++;
                    log.warn("Failed to parse patch of {}", raw.path(), ex);
                }
            }
            changedFiles.add(new ChangedFile(
                    raw.path(), raw.previousPath(), raw.changeType(), category, hunks));
            fileStats.put(raw.path(), raw.changeType().name().toLowerCase()
                    + ", +" + raw.additions() + "/-" + raw.deletions());
        }

        DeclarationIndex declarations = DeclarationIndex.empty();
        InvocationIndex invocations = InvocationIndex.empty();
        Map<String, String> headContents = new HashMap<>();
        List<String> headFiles = List.of();
        Capability snapshotCapability;
        int astAttempted = 0;
        int astFailed = 0;

        RepoSnapshot snapshot = null;
        try {
            snapshot = snapshotProvider.openHeadSnapshot(installationId, owner, repo, revision.headSha());
            snapshotCapability = snapshot.kind() == RepoSnapshot.Kind.ARCHIVE_FULL
                    ? Capability.COMPLETE : Capability.PARTIAL;
            headFiles = snapshot.listFiles();

            List<SourceInput> sources = new ArrayList<>();
            for (ChangedFile file : changedFiles) {
                if (!astProvider.supports(file.path()) || file.changeType() == ChangeType.DELETE
                        || (file.category() != FileCategory.SOURCE && file.category() != FileCategory.TEST)) {
                    continue;
                }
                astAttempted++;
                Optional<String> content = snapshot.readFile(file.path());
                content.ifPresent(text -> {
                    headContents.put(file.path(), text);
                    sources.add(new SourceInput(file.path(), text));
                });
            }
            ParseResult parse = astProvider.parse(sources);
            declarations = parse.declarations();
            invocations = parse.invocations();
            astFailed = parse.failures().size();
        } catch (Exception ex) {
            log.warn("Head snapshot unavailable for {}/{}@{}, continuing patch-only",
                    owner, repo, revision.headSha(), ex);
            snapshotCapability = Capability.MISSING;
        } finally {
            if (snapshot != null) {
                snapshot.close();
            }
        }

        AtomicInteger oldAttempted = new AtomicInteger();
        AtomicInteger oldLoaded = new AtomicInteger();
        Map<String, String> oldContents = new HashMap<>();
        Function<String, Optional<String>> oldLoader = path -> {
            oldAttempted.incrementAndGet();
            try {
                Optional<String> content = snapshotProvider.mergeBaseFileContent(
                        installationId, owner, repo, revision.mergeBaseSha(), path);
                content.ifPresent(text -> {
                    oldLoaded.incrementAndGet();
                    oldContents.put(path, text);
                });
                return content;
            } catch (IOException ex) {
                log.warn("Failed to load merge-base file {}", path, ex);
                return Optional.empty();
            }
        };
        OldSideAnalysis oldSide = oldSideAnalyzer.analyze(changedFiles, oldLoader);

        List<ChangedSymbol> changedSymbols = new ArrayList<>();
        for (ChangedFile file : changedFiles) {
            changedSymbols.addAll(symbolLocator.locateHead(file, declarations));
        }
        changedSymbols.addAll(oldSide.symbols());

        RecallInput recallInput = new RecallInput(changedFiles, changedSymbols,
                declarations, invocations, oldSide.declarations(), headFiles);
        List<ContextEdge> edges = enabledChannels().stream()
                .flatMap(channel -> channel.recall(recallInput).stream())
                .toList();

        if (snapshot != null) {
            for (ContextEdge edge : edges) {
                if (edge.from().revision() == RevisionSide.HEAD
                        && !headContents.containsKey(edge.from().path())) {
                    try {
                        snapshot.readFile(edge.from().path())
                                .ifPresent(content -> headContents.put(edge.from().path(), content));
                    } catch (IOException ex) {
                        log.warn("Failed to read related file {}", edge.from().path(), ex);
                    }
                }
            }
        }

        PlannedContext planned = planner.plan(new PlannerInput(
                changedFiles, changedSymbols, edges, headContents, oldContents, fileStats));

        List<ContextItem> required = planned.items().stream()
                .filter(ContextItem::required)
                .toList();
        List<SliceAssignment> assignments = slicer.slice(required, edges, properties.contextBudget());
        if (assignments.isEmpty()) {
            assignments = List.of(new SliceAssignment("slice-1", List.of()));
        }

        List<ReviewSlice> slices = new ArrayList<>();
        for (SliceAssignment assignment : assignments) {
            Set<String> paths = new HashSet<>(assignment.changedPaths());
            List<ContextItem> sliceItems = planned.items().stream()
                    .filter(item -> item.required()
                            ? paths.contains(item.ref().path())
                            : paths.contains(planned.optionalItemOwners().get(item.itemId())))
                    .toList();
            PackResult packed = packer.pack(sliceItems, properties.contextBudget(), policy::score);
            slices.add(new ReviewSlice(assignment.sliceId(), packed.included(), packed.dropped(),
                    packed.estimatedTokens(), properties.contextBudget()));
        }

        GlobalChangeSummary summary = new GlobalChangeSummary(
                changedFiles.size(),
                acquisition.patch().files().stream().mapToInt(RawFilePatch::additions).sum(),
                acquisition.patch().files().stream().mapToInt(RawFilePatch::deletions).sum(),
                assignments.stream().map(a -> a.changedPaths().isEmpty()
                        ? "misc" : commonPrefixDir(a.changedPaths())).toList());

        CapabilityReport capabilities = new CapabilityReport(
                missingPatches == 0 && parseFailures == 0 ? Capability.COMPLETE : Capability.PARTIAL,
                snapshotCapability,
                oldAttempted.get() == 0 || oldLoaded.get() == oldAttempted.get() ? Capability.COMPLETE
                        : oldLoaded.get() > 0 ? Capability.PARTIAL : Capability.MISSING,
                astAttempted == 0 ? 1.0 : (double) (astAttempted - astFailed) / astAttempted,
                properties.getRecall().isSymbolGraph() ? ChannelState.ENABLED : ChannelState.DISABLED_BY_CONFIG,
                properties.getRecall().isBm25() ? ChannelState.ENABLED : ChannelState.DISABLED_BY_CONFIG,
                properties.getRecall().isCoChange() ? ChannelState.ENABLED : ChannelState.DISABLED_BY_CONFIG);

        ReviewPlan plan = new ReviewPlan(
                ReviewPlan.CURRENT_SCHEMA_VERSION, contextId, revision, policy.version(),
                configFingerprint, summary, slices, capabilities);

        Map<String, String> diagnostics = new HashMap<>();
        diagnostics.put("missingPatches", String.valueOf(missingPatches));
        diagnostics.put("parseFailures", String.valueOf(parseFailures));
        diagnostics.put("sensitiveSkipped", String.valueOf(planned.sensitiveSkipped().size()));
        diagnostics.put("edgeCount", String.valueOf(edges.size()));
        contextStore.save(new ContextSnapshot(plan, diagnostics));

        log.info("Built context {} for {}/{}#{}: {} files, {} slices, {} edges",
                contextId, owner, repo, prNumber, changedFiles.size(), slices.size(), edges.size());
        return new ContextBuildResult.Ready(plan);
    }

    private List<RecallChannel> enabledChannels() {
        return recallChannels.stream()
                .filter(channel -> switch (channel.source()) {
                    case SYMBOL_GRAPH -> properties.getRecall().isSymbolGraph();
                    case CONVENTION -> properties.getRecall().isConventions();
                    case LEXICAL_BM25 -> properties.getRecall().isBm25();
                    case CO_CHANGE -> properties.getRecall().isCoChange();
                    case REQUIRED -> false;
                })
                .toList();
    }

    private static String commonPrefixDir(List<String> paths) {
        String prefix = parentDir(paths.get(0));
        for (String path : paths) {
            while (!parentDir(path).startsWith(prefix)) {
                int slash = prefix.lastIndexOf('/');
                if (slash < 0) {
                    return "multiple";
                }
                prefix = prefix.substring(0, slash);
            }
        }
        return prefix.isEmpty() ? "root" : prefix;
    }

    private static String parentDir(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }
}
