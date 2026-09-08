package com.evoreview.context.semantic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pass-1 output: who declares what. FQN collisions across modules are tolerated
 * (first wins in byFqn; byPath keeps them all) — ContextRef carries path anyway.
 */
public record DeclarationIndex(
        Map<String, TypeDecl> typesByFqn,
        Map<String, List<TypeDecl>> typesByPath
) {
    public static DeclarationIndex of(List<TypeDecl> types) {
        Map<String, TypeDecl> byFqn = new LinkedHashMap<>();
        Map<String, List<TypeDecl>> byPath = new LinkedHashMap<>();
        for (TypeDecl type : types) {
            byFqn.putIfAbsent(type.fqn(), type);
            byPath.computeIfAbsent(type.path(), k -> new ArrayList<>()).add(type);
        }
        return new DeclarationIndex(byFqn, byPath);
    }

    public static DeclarationIndex empty() {
        return new DeclarationIndex(Map.of(), Map.of());
    }

    public Optional<TypeDecl> byFqn(String fqn) {
        return Optional.ofNullable(typesByFqn.get(fqn));
    }

    public List<TypeDecl> byPath(String path) {
        return typesByPath.getOrDefault(path, List.of());
    }

    public List<TypeDecl> all() {
        return typesByFqn.values().stream()
                .sorted(Comparator.comparing(TypeDecl::fqn))
                .toList();
    }

    public List<TypeDecl> implementorsOf(String fqn) {
        return all().stream()
                .filter(type -> type.interfaces().contains(fqn) || fqn.equals(type.superClass()))
                .toList();
    }

    /** Best-effort syntactic resolution: exact FQN, then imports, then same package. */
    public Optional<TypeDecl> resolve(String simpleOrFqn, String packageName, List<String> imports) {
        if (simpleOrFqn.contains(".")) {
            Optional<TypeDecl> direct = byFqn(simpleOrFqn);
            if (direct.isPresent()) {
                return direct;
            }
        }
        for (String imp : imports) {
            if (imp.endsWith("." + simpleOrFqn)) {
                Optional<TypeDecl> found = byFqn(imp);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return byFqn(packageName == null || packageName.isEmpty()
                ? simpleOrFqn
                : packageName + "." + simpleOrFqn);
    }
}
