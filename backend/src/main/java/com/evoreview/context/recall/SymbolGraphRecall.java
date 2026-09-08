package com.evoreview.context.recall;

import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.model.ContextRef;
import com.evoreview.context.model.EdgeType;
import com.evoreview.context.model.FileCategory;
import com.evoreview.context.model.RecallSource;
import com.evoreview.context.model.RevisionSide;
import com.evoreview.context.parse.ChangeClassifier;
import com.evoreview.context.semantic.ChangedSymbol;
import com.evoreview.context.semantic.DeclarationIndex;
import com.evoreview.context.semantic.InvocationSite;
import com.evoreview.context.semantic.MethodDecl;
import com.evoreview.context.semantic.TypeDecl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * S1 channel: turns the declaration/invocation indices into typed edges.
 * Disambiguation is heuristic (receiver name, then imports, then package) and
 * every edge carries its confidence. Callers of merge-base-side (deleted or
 * rewritten) methods get a confidence floor — calling removed code is a
 * high-signal smell.
 */
public final class SymbolGraphRecall implements RecallChannel {

    private static final double RECEIVER_MATCH = 0.85;
    private static final double IMPORT_MATCH = 0.7;
    private static final double PACKAGE_MATCH = 0.6;
    private static final double OVERRIDE = 0.9;
    private static final double IMPLEMENTATION = 0.85;
    private static final double REMOVED_CODE_FLOOR = 0.9;

    private static final Comparator<ContextEdge> EDGE_ORDER = Comparator
            .comparing((ContextEdge edge) -> edge.type().name())
            .thenComparing(edge -> edge.to().path())
            .thenComparing(edge -> edge.to().startLine(), Comparator.nullsLast(Integer::compareTo))
            .thenComparing(edge -> edge.from().path())
            .thenComparing(edge -> edge.from().startLine(), Comparator.nullsLast(Integer::compareTo));

    private final ChangeClassifier classifier = new ChangeClassifier();

    @Override
    public RecallSource source() {
        return RecallSource.SYMBOL_GRAPH;
    }

    @Override
    public List<ContextEdge> recall(RecallInput input) {
        List<ContextEdge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ChangedSymbol symbol : input.changedSymbols()) {
            collectCallers(input, symbol, edges, seen);
            collectOverrides(input, symbol, edges, seen);
        }
        edges.sort(EDGE_ORDER);
        return List.copyOf(edges);
    }

    private void collectCallers(
            RecallInput input, ChangedSymbol symbol, List<ContextEdge> edges, Set<String> seen) {
        for (InvocationSite site : input.invocations().lookup(symbol.methodName(), symbol.arity())) {
            if (site.path().equals(symbol.path())) {
                continue;
            }
            Double confidence = callConfidence(input, symbol, site);
            if (confidence == null) {
                continue;
            }
            if (symbol.side() == RevisionSide.MERGE_BASE) {
                confidence = Math.max(confidence, REMOVED_CODE_FLOOR);
            }
            boolean test = classifier.classify(site.path(), null) == FileCategory.TEST;
            ContextRef from = new ContextRef(RevisionSide.HEAD, site.path(), site.line(), site.line(),
                    callerSymbolId(site), null);
            ContextRef to = new ContextRef(symbol.side(), symbol.path(), symbol.startLine(),
                    symbol.endLine(), symbol.symbolId(), null);
            addEdge(edges, seen, new ContextEdge(
                    test ? EdgeType.TESTED_BY : EdgeType.CALLS, from, to, confidence, source()));
        }
    }

    private Double callConfidence(RecallInput input, ChangedSymbol symbol, InvocationSite site) {
        String expectedReceiver = decapitalize(simpleName(symbol.classFqn()));
        String receiver = site.receiverName();
        if (receiver.equals(expectedReceiver) || receiver.endsWith("." + expectedReceiver)) {
            return RECEIVER_MATCH;
        }
        TypeDecl callerType = input.declarations().byPath(site.path()).stream()
                .findFirst()
                .orElse(null);
        if (callerType == null) {
            return null;
        }
        if (callerType.imports().contains(symbol.classFqn())) {
            return IMPORT_MATCH;
        }
        if (!callerType.packageName().isEmpty()
                && callerType.packageName().equals(packageOf(symbol.classFqn()))) {
            return PACKAGE_MATCH;
        }
        return null;
    }

    private void collectOverrides(
            RecallInput input, ChangedSymbol symbol, List<ContextEdge> edges, Set<String> seen) {
        DeclarationIndex ownIndex = input.declarationsFor(symbol);
        Optional<TypeDecl> changedType = ownIndex.byFqn(symbol.classFqn());
        if (changedType.isEmpty()) {
            return;
        }
        TypeDecl type = changedType.get();

        List<String> parents = new ArrayList<>(type.interfaces());
        if (type.superClass() != null) {
            parents.add(type.superClass());
        }
        for (String parentFqn : parents) {
            Optional<TypeDecl> parent = input.declarations().byFqn(parentFqn);
            RevisionSide parentSide = RevisionSide.HEAD;
            if (parent.isEmpty()) {
                parent = input.oldDeclarations().byFqn(parentFqn);
                parentSide = RevisionSide.MERGE_BASE;
            }
            if (parent.isEmpty()) {
                continue;
            }
            for (MethodDecl parentMethod : parent.get().methods()) {
                if (parentMethod.name().equals(symbol.methodName()) && parentMethod.arity() == symbol.arity()) {
                    ContextRef from = new ContextRef(parentSide, parent.get().path(),
                            parentMethod.startLine(), parentMethod.endLine(),
                            parent.get().symbolId(parentMethod), null);
                    addEdge(edges, seen, new ContextEdge(
                            EdgeType.OVERRIDES, from, symbolRef(symbol), OVERRIDE, source()));
                }
            }
        }

        for (TypeDecl implementor : input.declarations().implementorsOf(type.fqn())) {
            for (MethodDecl implMethod : implementor.methods()) {
                if (implMethod.name().equals(symbol.methodName()) && implMethod.arity() == symbol.arity()) {
                    ContextRef from = new ContextRef(RevisionSide.HEAD, implementor.path(),
                            implMethod.startLine(), implMethod.endLine(),
                            implementor.symbolId(implMethod), null);
                    addEdge(edges, seen, new ContextEdge(
                            EdgeType.OVERRIDES, from, symbolRef(symbol), IMPLEMENTATION, source()));
                }
            }
        }
    }

    private static ContextRef symbolRef(ChangedSymbol symbol) {
        return new ContextRef(symbol.side(), symbol.path(), symbol.startLine(), symbol.endLine(),
                symbol.symbolId(), null);
    }

    private static String callerSymbolId(InvocationSite site) {
        if (site.enclosingTypeFqn() == null || site.enclosingTypeFqn().isEmpty()
                || site.enclosingMethod() == null) {
            return null;
        }
        return site.enclosingTypeFqn() + "#" + site.enclosingMethod();
    }

    private void addEdge(List<ContextEdge> edges, Set<String> seen, ContextEdge edge) {
        String key = edge.type() + "|" + edge.from().path() + "|" + edge.from().startLine()
                + "|" + edge.to().path() + "|" + edge.to().symbolId();
        if (seen.add(key)) {
            edges.add(edge);
        }
    }

    private static String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    private static String packageOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? "" : fqn.substring(0, dot);
    }

    private static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
