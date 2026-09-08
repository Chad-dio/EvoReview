package com.evoreview.context.semantic;

import java.util.List;
import java.util.Objects;

/**
 * One parsed type. superClass and interfaces hold best-effort resolved FQNs
 * (import match, else same package) — syntactic resolution only, no classpath.
 */
public record TypeDecl(
        String fqn,
        String simpleName,
        String packageName,
        String path,
        TypeKind kind,
        String superClass,
        List<String> interfaces,
        List<MethodDecl> methods,
        List<String> imports
) {
    public TypeDecl {
        Objects.requireNonNull(fqn, "fqn");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
        methods = methods == null ? List.of() : List.copyOf(methods);
        imports = imports == null ? List.of() : List.copyOf(imports);
    }

    public String symbolId(MethodDecl method) {
        return fqn + "#" + method.signature();
    }
}
