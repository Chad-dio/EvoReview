package com.evoreview.context.semantic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Single-pass JavaParser implementation: every parse emits both the declaration
 * index and the invocation index. Syntactic only — no classpath resolution —
 * so a small false-positive rate in related code is expected and tolerated.
 * A file that fails to parse is recorded in ParseResult.failures, never thrown.
 */
@Component
public class JavaAstProvider implements AstProvider {

    @Override
    public boolean supports(String path) {
        return path != null && path.endsWith(".java");
    }

    @Override
    public ParseResult parse(List<SourceInput> sources) {
        JavaParser parser = new JavaParser(
                new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        List<TypeDecl> types = new ArrayList<>();
        Map<InvocationKey, List<InvocationSite>> invocations = new LinkedHashMap<>();
        List<ParseFailure> failures = new ArrayList<>();

        for (SourceInput source : sources) {
            if (!supports(source.path())) {
                continue;
            }
            try {
                com.github.javaparser.ParseResult<CompilationUnit> result = parser.parse(source.content());
                if (!result.isSuccessful() || result.getResult().isEmpty()) {
                    failures.add(new ParseFailure(source.path(), result.getProblems().toString()));
                    continue;
                }
                CompilationUnit unit = result.getResult().get();
                String packageName = unit.getPackageDeclaration()
                        .map(decl -> decl.getNameAsString())
                        .orElse("");
                List<String> imports = unit.getImports().stream()
                        .map(ImportDeclaration::getNameAsString)
                        .toList();

                for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
                    types.add(toTypeDecl(type, source.path(), packageName, imports));
                }
                for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
                    String receiver = call.getScope().map(Expression::toString).orElse("");
                    String enclosingType = call.findAncestor(TypeDeclaration.class)
                            .flatMap(JavaAstProvider::fqnOf)
                            .orElse("");
                    String enclosingMethod = call.findAncestor(MethodDeclaration.class)
                            .map(MethodDeclaration::getNameAsString)
                            .orElse(null);
                    int line = call.getBegin().map(position -> position.line).orElse(0);
                    InvocationIndex.record(
                            invocations,
                            new InvocationSite(source.path(), line, receiver, enclosingType, enclosingMethod),
                            call.getNameAsString(),
                            call.getArguments().size());
                }
            } catch (Exception ex) {
                failures.add(new ParseFailure(source.path(), String.valueOf(ex.getMessage())));
            }
        }
        return new ParseResult(
                DeclarationIndex.of(types),
                InvocationIndex.of(invocations),
                failures);
    }

    private static java.util.Optional<String> fqnOf(TypeDeclaration<?> type) {
        return type.getFullyQualifiedName();
    }

    private static TypeDecl toTypeDecl(
            TypeDeclaration<?> type, String path, String packageName, List<String> imports) {
        String fqn = type.getFullyQualifiedName()
                .orElse(packageName.isEmpty() ? type.getNameAsString()
                        : packageName + "." + type.getNameAsString());

        String superClass = null;
        List<String> interfaces = List.of();
        if (type instanceof ClassOrInterfaceDeclaration declaration) {
            superClass = declaration.getExtendedTypes().stream()
                    .findFirst()
                    .map(extended -> resolve(extended.getNameAsString(), imports, packageName))
                    .orElse(null);
            interfaces = declaration.getImplementedTypes().stream()
                    .map(implemented -> resolve(implemented.getNameAsString(), imports, packageName))
                    .toList();
        }

        List<MethodDecl> methods = type.getMethods().stream()
                .map(JavaAstProvider::toMethodDecl)
                .toList();

        return new TypeDecl(
                fqn,
                type.getNameAsString(),
                packageName,
                path,
                kindOf(type),
                superClass,
                interfaces,
                methods,
                imports);
    }

    private static MethodDecl toMethodDecl(MethodDeclaration method) {
        String signature = method.getNameAsString() + method.getParameters().stream()
                .map(parameter -> parameter.getType().asString())
                .collect(Collectors.joining(",", "(", ")"));
        int startLine = method.getBegin().map(position -> position.line).orElse(-1);
        int endLine = method.getEnd().map(position -> position.line).orElse(-1);
        return new MethodDecl(
                method.getNameAsString(), signature, method.getParameters().size(), startLine, endLine);
    }

    private static TypeKind kindOf(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration declaration) {
            return declaration.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS;
        }
        if (type instanceof EnumDeclaration) {
            return TypeKind.ENUM;
        }
        if (type instanceof RecordDeclaration) {
            return TypeKind.RECORD;
        }
        if (type instanceof AnnotationDeclaration) {
            return TypeKind.ANNOTATION;
        }
        return TypeKind.CLASS;
    }

    private static String resolve(String simpleName, List<String> imports, String packageName) {
        for (String imp : imports) {
            if (imp.endsWith("." + simpleName)) {
                return imp;
            }
        }
        return packageName == null || packageName.isEmpty()
                ? simpleName
                : packageName + "." + simpleName;
    }
}
