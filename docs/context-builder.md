# Context Builder (Phase 2)

Status: **Design proposal v3 — supersedes all earlier drafts. Not implemented yet.**

> Turn a PR diff into a retrieval query, find the code the reviewer actually needs from
> an immutable repository corpus, select deterministically under a fixed budget, and
> freeze the result into a replayable `ReviewPlan`.

The Context Builder is a small information-retrieval system — recall → rank → pack →
evaluate → evolve — not a "feed the diff to an LLM" pipe. Review quality is bounded by
recall: code the reviewer never sees is code that is never reviewed.

## 1. Design principles

These principles outrank any individual mechanism below:

1. **Immutable corpus in, frozen plan out.** The builder consumes immutable inputs
   (revision spec, snapshot, history snapshot) and never calls GitHub or the network
   itself. Acquisition is a separate layer behind provider interfaces. This is what makes
   Phase 7 historical replay possible.
2. **Capabilities are truth; levels are presentation.** Internally the builder reports a
   structured `CapabilityReport`; user-facing "quality levels" are derived from it, never
   the other way around.
3. **As-of temporal correctness.** Any feature derived from history (co-change, file
   hotness, feedback statistics) must be computed *as of the PR's time*. Replaying a 2024
   PR with an index trained on 2024–2026 history is temporal leakage and inflates
   offline metrics. This is a hard contract on `HistoryProvider`, not a best practice.
4. **Byte-determinism.** Same `CorpusKey` + policy + config ⇒ byte-identical canonical
   snapshot. Operational metadata (timestamps, latency, cache hits) lives outside the
   canonical form.
5. **Harness before policy.** No retrieval channel or ranking policy ships enabled on
   faith; it must win on the evaluation harness (§8).
6. **Structured references from day one.** Every piece of context carries a
   `ContextRef` (revision side, path, line range, symbol id, content hash). Phase 3
   comment anchoring and Phase 6 attribution depend on it.

## 2. Goals / Non-goals

Goals:

- Correct line anchoring on **both** revision sides (Phase 3 inline comments).
- Semantic depth: changed methods, callers, overrides, tests — not bare hunks.
- Large-PR scalability via slicing; `required` context is never silently dropped.
- Versioned, evolvable selection policy (`ContextPolicy`), orthogonal to reviewer
  prompt/rule versions.
- Everything replayable offline: snapshots + labeled benchmark + determinism suite.

Non-goals:

- Agentic tool-use exploration — deferred to Phase 5+, and only with harness evidence
  (see §6, "Agentic deep-dive").
- Whole-program call graphs / data-flow slicing (WALA/Soot) — re-evaluate in Phase 5+.
- JavaSymbolSolver full-classpath resolution — heuristic disambiguation is good enough;
  the reviewer tolerates a small false-positive rate in *related* code.
- Vector retrieval / embeddings — that is Phase 5 knowledge, not PR context.
- Distributed cache / persistent queue — interfaces tolerate it; Phase 4 lands it.
- Learned ranking policies — Phase 2 ships `heuristic-v1` only.

## 3. Core abstractions

### 3.1 RevisionSpec — what "this PR" precisely means

GitHub PR diffs use three-dot (merge-base) semantics: the diff is `mergeBase...head`,
**not** `base...head`. `base.sha` is the moving tip of the target branch and is not a
valid old-side revision. The model records all three:

```java
public record RevisionSpec(String repoId, int prNumber,
                           String baseSha, String mergeBaseSha, String headSha,
                           String patchSha) {}
// patchSha: hash of the normalized patch set, so corpus identity does not
// depend on GitHub recomputing the same diff twice.
```

`mergeBaseSha` is obtained from the compare API (`compare/{base}...{head}` →
`merge_base_commit`) at acquisition time and frozen into the spec.

### 3.2 RevisionWorkspace — one full snapshot, one lazy side

We do **not** materialize two full repo snapshots (IO/AST cost for no benefit). But a
head-only snapshot cannot answer questions about *deleted methods, removed signatures,
old implementations, or rename sources* — code the reviewer often most needs to see.

```text
RevisionWorkspace
├── head/   full snapshot at headSha        (zipball, guarded — D1)
└── base/   lazily materialized files at mergeBaseSha:
            deleted files, rename sources, and changed files
            when old-side symbols are referenced
```

### 3.3 Two-layer identity: CorpusKey vs ContextId

"Which code world did we see" and "what did the policy select from it" are different
questions and get different IDs:

```text
CorpusKey = sha256(repoId + mergeBaseSha + headSha + patchSha + acquisitionVersion)

ContextId = sha256(CorpusKey + contextSchemaVersion + builderVersion
                   + policyVersion + configFingerprint + tokenEstimatorVersion)
```

Raising `max-tokens`, flipping a recall channel, or changing the tokenizer all change
`ContextId`, so content-addressed semantics stay honest. One `CorpusKey` can be replayed
under `policy-v1 / v2 / v3` — exactly what Phase 7 comparisons need.

### 3.4 ContextRef — structured identity for every span

```java
public enum RevisionSide { HEAD, MERGE_BASE }

public record ContextRef(RevisionSide revision, String path,
                         Integer startLine, Integer endLine,
                         String symbolId, String contentSha) {}
// symbolId: "com.example.Foo#bar(java.lang.String)"
// FQN collisions across modules/source-sets are disambiguated because
// ContextRef always pairs symbolId with path + revision.
```

### 3.5 ContextItem / ContextEdge

```java
public record ContextItem(String itemId, ItemKind kind, ContextRef ref, String content,
                          int estimatedTokens, boolean required, List<String> requires,
                          List<RecallSource> sources, Map<String, Double> features) {}

public record ContextEdge(EdgeType type, ContextRef from, ContextRef to,
                          double confidence, RecallSource source) {}
// EdgeType: CALLS / OVERRIDES / TESTED_BY
```

- `required`: diff hunks and full changed-method bodies **within the same slice** (§3.7).
- `sources`: every recall channel that surfaced this item (S1/S2/S3/S4) — the provenance
  that lets the evolution loop compute per-channel useful rates.
- `features`: reranker inputs (edge type, change distance, channel, file hotness, ...).

### 3.6 Snapshot vs telemetry — determinism is preserved by separation

```java
// Canonical, byte-reproducible; persisted via ContextStore. Contains NO timestamps,
// durations, cache stats, or absolute local paths.
public record ContextSnapshot(ReviewPlan plan, Map<String, String> deterministicDiagnostics) {}

// Operational; logged and metered, never inside the snapshot.
public record ContextBuildTelemetry(String contextId, String deliveryId, Instant builtAt,
                                    long durationMs, double cacheHitRate, String node) {}
```

A `builtAt` field inside the canonical form would make "build 10× → byte-identical"
impossible by construction. Diagnostics inside the snapshot must be content-derived and
stable (e.g. per-file parse-failure flags), never runtime-derived.

### 3.7 ReviewPlan / ReviewSlice — resolving required vs budget

"One PR = one context, required never dropped, Σ tokens ≤ 8000" is mathematically
unsatisfiable for a 40-file / 120-method PR. So the unit changes:

```text
one PR = one ReviewPlan
              ├── GlobalChangeSummary
              ├── ReviewSlice 1   (required ⊆ slice, Σ tokens ≤ slice budget)
              ├── ReviewSlice 2
              └── ReviewSlice 3
```

```java
public record ReviewSlice(String sliceId, List<ContextItem> included,
                          List<DroppedItem> dropped, int estimatedTokens, int budget) {}

public record ReviewPlan(int schemaVersion, String contextId, RevisionSpec revision,
                         String policyVersion, String configFingerprint,
                         GlobalChangeSummary summary, List<ReviewSlice> slices,
                         CapabilityReport capabilities) {}
```

- Slices are formed by **changed-symbol clustering**: changed files grouped by
  symbol-graph connectivity + package cohesion (e.g. an `Auth*` cluster vs a `Payment*`
  cluster). A small PR produces one slice.
- `required` holds *within a slice*; slicing is what keeps the invariant satisfiable.
- Changed files are **never** dropped by a file-count cap. The cap applies to related
  files only: `max-related-files-per-slice`.
- `GlobalChangeSummary` (file stats, cluster themes) lets Phase 3 dedup/merge findings
  across slices.

### 3.8 CapabilityReport

```java
public record CapabilityReport(Capability patch,          // COMPLETE / PARTIAL / MISSING
                               Capability headSnapshot,
                               Capability mergeBaseFiles,
                               double astCoverage,
                               ChannelState symbolRecall, // ENABLED / DISABLED_BY_CONFIG /
                               ChannelState lexicalRecall, // UNAVAILABLE_NO_HISTORY / FAILED
                               ChannelState coChange) {}
```

A linear L0–L4 ladder cannot express "snapshot OK, AST 93%, S1 on, S2 off by config,
history unavailable". Capabilities are the truth; a presentation level may be derived
for humans. Every fallback and its reason lands here and in `diagnostics`.

### 3.9 ContextBuildResult — the builder never talks back to GitHub

The builder returns `ContextBuildResult = Ready(ReviewPlan) | Skipped(reason)`. Whether a
skip becomes a "change too large / fetch failed" PR comment is decided by
`PullRequestEventService` / the Phase 3 presentation layer. `com.evoreview.context`
stays pure: immutable inputs in, data out.

## 4. Pipeline

```text
pull_request webhook (async, per-PR serialized — §5 D5)
        ↓
Acquisition    PatchProvider        file list + unified diffs (+ patchSha)
               RepoSnapshotProvider HEAD zipball + lazy MERGE_BASE files
               HistoryProvider      immutable HistorySnapshot (as-of) for co-change
        ↓
Parsing        DiffParser           patch → Hunk/ChangedLine, dual line cursors
               ChangeClassifier     ADD/MODIFY/DELETE/RENAME + generated/binary/lockfile
               SensitiveFileClassifier  .env / *.pem / keys / credentials (D7)
        ↓
Semantic       JavaAstProvider      single pass → DeclarationIndex + InvocationIndex
               OldSideParser        merge-base files for deleted/renamed/changed symbols
        ↓
Recall         S1 symbol graph · S4 conventions          (Phase 2B, on by default)
               S2 lexical BM25 · S3 git co-change        (Phase 2E, off until harness win)
        ↓
Rerank         ContextPolicy (interface) → heuristic-v1  (Phase 2C; learned policies later)
        ↓
Pack+Slice     closure-aware deterministic coverage greedy (D9) → ReviewPlan
        ↓
Freeze         ContextSnapshot via ContextStore; telemetry emitted separately
        ↓
(Phase 3) POST /review to the LLM service
```

## 5. Design decisions

### D1 — Acquisition contract has three providers

```text
acquisition/
├── PatchProvider          PR file list + per-file unified diff; normalizes and hashes (patchSha)
├── RepoSnapshotProvider   HEAD zipball (1 HTTP request, size/path-guarded);
│                          lazy MERGE_BASE materialization via contents API
│                          (deleted, renamed, old-side-needed files only);
│                          fallback: per-file contents API when repo > max-snapshot-mb
└── HistoryProvider        immutable HistorySnapshot: commit list truncated AS OF the PR,
                           backing S3 co-change without temporal leakage (principle 3)
```

The zipball alone cannot serve co-change mining — it has no `.git`. That is why history
is an explicit acquisition concern rather than something the context layer sneaks around
the boundary to fetch.

### D2 — One parse pass, two indices; no circular "plausibly related" scan

Scanning "files plausibly related to changed methods" to find callers is circular: the
callers are what we are trying to find. Instead the single JavaParser pass over the HEAD
snapshot emits both:

```java
public record DeclarationIndex(Map<String, TypeDecl> types,
                               Multimap<String, String> methodNameIndex) {}

public record InvocationIndex(Multimap<InvocationKey, InvocationSite> sites) {}
// InvocationKey = methodName + arity.
// InvocationSite carries file, line, receiver simple name, package, imports.
```

Edge extraction for a changed method `save(User)`:

1. query `InvocationIndex` by `(save, arity=1)` — recall set;
2. filter by receiver type / imports / package / inheritance — precision;
3. emit `CALLS` edges with a `confidence` score; `OVERRIDES` from declaration
   hierarchies; `TESTED_BY` from test-source-set invocations + naming conventions.

Old-side semantics: the lazily materialized merge-base files are parsed the same way, so
a *deleted* method still yields its old declaration, old callers, and a `MERGE_BASE`-side
`ContextRef`. Parse failures are flagged per file in diagnostics; the build never aborts
on one bad file.

### D3 — Every candidate is a ContextItem (see §3.5)

Required/requires/sources/features as defined above. `requires` forms a dependency
closure: a method body pulls its class header + imports; a caller snippet pulls its
location note. The packer prices closures, not bare items (D9).

### D4 — Determinism and idempotency

- Canonical snapshot excludes all operational metadata (§3.6).
- Stable sorts with explicit tie-breakers (path, then line, then itemId); no `HashMap`
  iteration order, filesystem order, or executor completion order may leak into output.
- Entry idempotency: an existing snapshot for the same `ContextId` is reused; an existing
  `CorpusKey` allows policy-only rebuilds without re-acquisition.

### D5 — Per-PR serialization and coalescing

Builds for the same `repo#pr` serialize through an in-memory lock
(`ConcurrentHashMap<PrKey, CompletableFuture>` over the existing event executor). A newer
event **coalesces** a queued older one — we never build for a stale head. Phase 4 swaps
this for a persistent queue behind the same interface.

### D6 — Multi-language via SPI, implemented for Java only

```java
public interface AstProvider {
    boolean supports(String path);                       // by extension
    ParseResult parse(Path snapshotRoot, List<String> files);
}
```

Non-Java files go through `PatchOnlyProvider`. The SPI keeps Java logic out of the
generic layers; note that recall channels S2 (lexical) and S3 (co-change) are
language-agnostic for free.

### D7 — Safety: injection provenance, noise, and secrets

- **Prompt-injection provenance:** every `ContextItem` carries `kind` + `ContextRef`, so
  Phase 3 can wrap content in source-tagged, clearly delimited blocks and instruct the
  model that code is data, not instructions.
- **Noise control:** `ChangeClassifier` excludes from AST and packing: `*_pb2.java`,
  lockfiles, `*.min.js`, `Code generated ... DO NOT EDIT` headers, files over
  `max-file-kb`. Excluded files remain as metadata entries so the review can mention them.
- **Sensitive files:** `SensitiveFileClassifier` excludes `.env*`, `*.pem`, `*.key`,
  `*.p12`, `*.jks`, `credentials*`, `secrets*` from recall — even when a lexical channel
  would match. Exception: the file is itself a changed file **and** policy explicitly
  allows it. Full-repo retrieval makes this a real risk, not a theoretical one.
- **Archive safety:** total/per-file size caps, zip-slip checks (D1).

### D8 — Degradation is a CapabilityReport, and skipping is a result

Per principle 2 and §3.8/§3.9: partial capability (e.g. AST coverage 0.93, co-change
unavailable) is recorded structurally; the webhook thread never fails because of context
building; a complete failure returns `Skipped(reason)` and the caller decides whether to
post anything.

### D9 — Packing: deterministic budgeted coverage greedy (no optimality claim)

Honest naming: this is a **deterministic coverage-greedy heuristic**, not a submodular
optimizer with a `(1−1/e)` guarantee. That classical bound applies to monotone submodular
maximization under a *cardinality* constraint with unit-cost greedy; under a token
knapsack with dependency-closure costs it does not transfer, and we do not claim it.

The algorithm:

```text
1. required items of the slice: always in (slicing keeps this satisfiable, §3.7)
2. optional items, repeatedly pick argmax:
       marginalCoverageGain(S ∪ closure(i)) − redundancyWith(S)
       ────────────────────────────────────────────────────────
              marginalTokens(closure(i) \ S)
3. deterministic tie-break: (path, startLine, itemId)
```

where `closure(i)` includes unmet `requires` — real cost is the closure, not the item.
Packing *variants* (random / fixed priority / value-density / coverage greedy) are
compared empirically in the harness (§8); if a strictly submodular objective is later
formalized, the guarantee can be earned then. Dropped items are recorded with
`dropReason` in `ReviewSlice.dropped`; the prompt may state "N related files omitted",
and Phase 6 can correlate drops with review quality.

### D10 — Recall channels

| Channel | Source | Status | Notes |
|---|---|---|---|
| S1 symbol graph | DeclarationIndex + InvocationIndex edges | **default on** (2B) | precise, typed |
| S4 conventions | `Foo ↔ FooTest`, config pairs | **default on** (2B) | cheap, high precision |
| S2 lexical BM25 | changed identifiers + error strings vs repo | **off by default** (2E) | language-agnostic; must beat baseline in harness |
| S3 git co-change | association rules over HistorySnapshot (Zimmermann-style MSR) | **off by default** (2E) | catches evolutionary coupling invisible to AST; **as-of index only** (principle 3); auto-disabled below `min-history-commits` |

Candidate items carry the channel(s) that produced them (`sources`), which is what makes
per-channel useful-rate analysis possible later.

### D11 — Rerank policy is a versioned artifact, and it exists in Phase 2

`ContextPolicy` (score a candidate against the change summary) is introduced in
**Phase 2C** — the packer needs it, so deferring the interface to "3.x" was an ordering
bug in earlier drafts. Versioning is orthogonal to reviewer versions: when review quality
moves, we can tell "prompt improved" from "context retrieval improved".

- `heuristic-v1`: hand-tuned weights over `features`. Ships with 2C.
- Learned policies (logistic regression / simple LTR over attribution data): Phase 3.x–4,
  promoted only via harness comparison. `policyVersion` is inside `ContextId` (§3.3).

### D12 — Agentic deep-dive: deferred to Phase 5+

An LLM tool-use loop that pulls extra context for suspicious hotspots is architecturally
compatible (its fetches go through the same providers; its tool trace would be recorded
into the snapshot and **replayed verbatim** instead of re-calling the model, caging the
nondeterminism). But it is excluded from Phase 2 scope: it adds cost, latency, and
evaluation complexity before the deterministic baseline and harness exist to judge it.

## 6. Evaluation harness — defined honestly

The earlier draft's biggest flaw was its gold standard. Historical human comment
**anchors** are finding *targets*, not the *supporting context* the reviewer needed — and
since diff hunks are `required`, anchor coverage is ≈100% by construction, making it
useless for judging retrieval channels. The harness therefore has four distinct layers:

| Layer | Metric | Gold source | What it proves |
|---|---|---|---|
| M1 | **Review Anchor Coverage** | historical comment anchors (free) | diff parsing + line anchoring + changed-method expansion are correct. A regression alarm, **not** a retrieval-quality metric |
| M2 | **Supporting Context Recall** | small hand-labeled benchmark: 30–50 historical PRs, per finding list `target + supporting symbols` | the true retrieval recall: `recalled gold support / gold support` |
| M3 | **Context Utilization & Cost** | finding→context citations (Phase 3 attribution) | per-channel/per-kind usage and tokens-per-finding. Deliberately **not** called "precision": uncited context can still prevent false positives, and citations can be gamed |
| M4 | **Downstream Quality** | replay set | the arbiter: channel/policy ablations (`diff-only`, `S1`, `S1+S4`, `+S2`, `+S3`) × finding precision / recall / acceptance / FPR |

Plus two suites:

- **Determinism suite:** same corpus, with deliberately shuffled file input order,
  candidate order, map insertion order, and executor completion order ⇒ canonical JSON
  byte-identical (stronger than "run 10×"); plus plain 10× repeat.
- **Capability audit:** distribution of `CapabilityReport` states across replays, so
  silent quality loss is visible.

## 7. Token budget model

`chars/4` estimation is fine for MVP, but the packer must not confuse *context* budget
with *model input* budget — Phase 3 adds instructions, JSON schema, refs, and rules on
top:

```text
modelInputBudget        (e.g. 12k)
− fixedPromptOverhead   (per prompt version, measured, e.g. 2k)
− safetyMargin          (e.g. 1k)
= contextBudget         (e.g. 9k) → divided across slices
```

`TokenEstimator` is an interface (`chars-div-4-v1` now); `tokenEstimatorVersion` is part
of `configFingerprint`, so switching to an exact tokenizer later stays replayable.

## 8. Configuration reference

```yaml
evoreview:
  context:
    model-input-budget: 12000
    prompt-overhead: 2000
    safety-margin: 1000
    max-related-files-per-slice: 30   # changed files are never capped
    max-file-kb: 256
    max-snapshot-mb: 50
    store-dir: .local/contexts
    policy: heuristic-v1
    token-estimator: chars-div-4-v1
    recall:
      symbol-graph: true
      conventions: true
      bm25: false                     # enable only after harness win
      co-change: false                # enable only after harness win
    co-change:
      min-history-commits: 200
      refresh-interval: 7d            # as-of truncation always applies on replay
    sensitive-patterns: [".env*", "*.pem", "*.key", "*.p12", "*.jks", "credentials*", "secrets*"]
```

## 9. Milestones — Phase 2 rescoped (2A → 2E)

**Phase 2A — Correctness Foundation**

| PR | Deliverable | Verified by |
|---|---|---|
| 2A.1 | `RevisionSpec` (+ mergeBase via compare API), `PatchProvider` (+patchSha), model records (`ContextRef`, `ContextItem`, ...) | unit tests |
| 2A.2 | `DiffParser` + `ChangeClassifier` + `SensitiveFileClassifier` | golden diffs + property tests (§10) |
| 2A.3 | canonical `ContextSnapshot` serialization, `CorpusKey`/`ContextId`, file `ContextStore`, telemetry split | determinism suite (shuffled orders) |
| 2A.4 | `RepoSnapshotProvider`: HEAD zipball + lazy merge-base materialization + contents-API fallback | WireMock contract tests (incl. null-patch large PR) |

**Phase 2B — Semantic Baseline**

| PR | Deliverable | Verified by |
|---|---|---|
| 2B.1 | `JavaAstProvider`: single pass → DeclarationIndex + InvocationIndex (HEAD) | fixture repo |
| 2B.2 | old-side parsing for deleted/renamed/changed files | fixture: deleted method still resolvable with MERGE_BASE refs |
| 2B.3 | S1 `SymbolGraphRecall`: CALLS / OVERRIDES / TESTED_BY with confidence | fixture edge-set assertions |
| 2B.4 | S4 `ConventionRecall` | fixture tests |

**Phase 2C — Selection**

| PR | Deliverable | Verified by |
|---|---|---|
| 2C.1 | `ContextPolicy` interface + `heuristic-v1` | unit tests |
| 2C.2 | closure-aware coverage-greedy packer | required-always-included, tie-break determinism |
| 2C.3 | changed-symbol clustering → `ReviewPlan`/`ReviewSlice` | large synthetic PR → N satisfiable slices |
| 2C.4 | wire into `PullRequestEventService`; `ContextBuildResult` incl. `Skipped`; PR comment v2 posted by the caller | E2E: comment shows slices, changed methods, capabilities |

**Phase 2D — Evaluation**

| PR | Deliverable | Verified by |
|---|---|---|
| 2D.1 | harness: M1 anchor coverage + determinism + capability audit | run on 5–10 historical PRs |
| 2D.2 | labeled supporting-context benchmark (30–50 PRs) → M2 | recall report for `S1+S4` baseline |
| 2D.3 | downstream baseline (M4) on replay set | `diff-only` vs `S1+S4` comparison |

**Phase 2E — Experimental recall (off by default)**

| PR | Deliverable | Verified by |
|---|---|---|
| 2E.1 | S2 BM25 | harness M2/M4 comparison vs baseline |
| 2E.2 | `HistoryProvider` + as-of co-change index (S3) | harness M2/M4 + leakage test (index must not see post-PR commits) |

Agentic deep-dive: Phase 5+, only with harness evidence (D12).

## 10. Testing strategy

- **Parser property tests.** Roundtrip is necessary but not sufficient; anchor invariants
  are what Phase 3 actually depends on:
  - old file + parsed hunks reconstructs the new file exactly;
  - every `ADDED` HunkLine satisfies `content == headFile[newLineNo]`;
  - every `REMOVED` HunkLine satisfies `content == mergeBaseFile[oldLineNo]`.
- **Parser fixtures:** `\ No newline at end of file`, CRLF, empty file, rename, delete,
  Unicode, multi-hunk, binary, null patch.
- **Fixture repo** under `src/test/resources/fixture-repo`: inheritance,
  multi-implementation interfaces, test conventions, generated files, same-FQN classes in
  two modules.
- **Determinism suite** as in §6.
- **Contract tests:** WireMock recordings of GitHub API responses (files, compare,
  zipball, contents), including the patch-less large-PR case.
- GitHub-facing code stays behind provider interfaces and is mocked, matching the
  existing `@MockBean` style.

## 11. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Anchor coverage mistaken for retrieval quality | four-layer metrics (§6); M1 documented as a regression alarm only |
| Temporal leakage in history-derived features | `HistoryProvider` as-of truncation contract (principle 3); leakage test in 2E.2 |
| `required` exceeds budget on huge PRs | slicing (§3.7); cap applies to related files only |
| FQN collisions in multi-module repos | structured `ContextRef` (path + revision + range alongside symbolId) |
| Sensitive files recalled into prompts | `SensitiveFileClassifier` (D7) with policy-gated changed-file exception |
| Deeper recall dilutes signal | channels off by default; M4 downstream comparison decides promotion |
| Nondeterminism via runtime metadata | snapshot/telemetry split (§3.6); determinism suite with shuffled orders |
| Rate limits (5000 req/h installation token) | zipball is O(1); merge-base materialization is lazy and cached |
