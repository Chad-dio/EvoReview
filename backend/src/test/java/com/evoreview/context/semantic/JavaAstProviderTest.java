package com.evoreview.context.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaAstProviderTest {

    private final JavaAstProvider provider = new JavaAstProvider();

    @Test
    void parsesTypeDeclarationsWithMethodsAndHierarchy() {
        ParseResult result = provider.parse(FixtureRepo.sources());

        assertThat(result.failures()).isEmpty();

        TypeDecl fooService = result.declarations().byFqn("com.example.service.FooService").orElseThrow();
        assertThat(fooService.kind()).isEqualTo(TypeKind.CLASS);
        assertThat(fooService.methods())
                .extracting(MethodDecl::signature)
                .containsExactlyInAnyOrder("save(User)", "save(User,boolean)", "validate(User)");
        assertThat(fooService.methods())
                .filteredOn(method -> method.name().equals("save"))
                .extracting(MethodDecl::arity)
                .containsExactlyInAnyOrder(1, 2);

        TypeDecl retryPolicy = result.declarations().byFqn("com.example.policy.RetryPolicy").orElseThrow();
        assertThat(retryPolicy.kind()).isEqualTo(TypeKind.INTERFACE);

        TypeDecl impl = result.declarations().byFqn("com.example.policy.DefaultRetryPolicy").orElseThrow();
        assertThat(impl.interfaces()).containsExactly("com.example.policy.RetryPolicy");

        assertThat(result.declarations().implementorsOf("com.example.policy.RetryPolicy"))
                .extracting(TypeDecl::fqn)
                .containsExactly("com.example.policy.DefaultRetryPolicy");
    }

    @Test
    void recordsMethodRanges() {
        ParseResult result = provider.parse(FixtureRepo.sources());

        TypeDecl fooService = result.declarations().byFqn("com.example.service.FooService").orElseThrow();
        MethodDecl save = fooService.methods().stream()
                .filter(method -> method.arity() == 1)
                .findFirst()
                .orElseThrow();

        String[] lines = FixtureRepo.sources().stream()
                .filter(source -> source.path().equals(FixtureRepo.FOO_SERVICE))
                .findFirst()
                .orElseThrow()
                .content()
                .split("\n");
        assertThat(lines[save.startLine() - 1]).contains("public void save(User user)");
        assertThat(save.endLine()).isGreaterThan(save.startLine());
    }

    @Test
    void recordsInvocationSitesWithReceiverAndEnclosingMethod() {
        ParseResult result = provider.parse(FixtureRepo.sources());

        List<InvocationSite> saveCalls = result.invocations().lookup("save", 1);

        assertThat(saveCalls).anySatisfy(site -> {
            assertThat(site.path()).isEqualTo(FixtureRepo.FOO_CALLER);
            assertThat(site.receiverName()).isEqualTo("fooService");
            assertThat(site.enclosingMethod()).isEqualTo("handle");
            assertThat(site.enclosingTypeFqn()).isEqualTo("com.example.service.FooCaller");
        });
        assertThat(saveCalls).anySatisfy(site -> {
            assertThat(site.path()).isEqualTo(FixtureRepo.ORDER_CALLER);
            assertThat(site.receiverName()).isEqualTo("service");
        });
        assertThat(saveCalls).anySatisfy(site ->
                assertThat(site.path()).isEqualTo(FixtureRepo.REPO_CALLER));
        assertThat(saveCalls).anySatisfy(site ->
                assertThat(site.path()).isEqualTo(FixtureRepo.FOO_SERVICE_TEST));
    }

    @Test
    void resolvesNestedClassFqn() {
        ParseResult result = provider.parse(List.of(new SourceInput("src/main/java/com/example/Outer.java", """
                package com.example;

                public class Outer {
                    public static class Inner {
                        public void run() {
                        }
                    }
                }
                """)));

        TypeDecl inner = result.declarations().byFqn("com.example.Outer.Inner").orElseThrow();
        assertThat(inner.methods()).extracting(MethodDecl::name).containsExactly("run");
    }

    @Test
    void recordsParseFailureAndKeepsGoing() {
        List<SourceInput> sources = List.of(
                new SourceInput("src/main/java/Broken.java", "public class {"),
                new SourceInput(FixtureRepo.USER, """
                        package com.example.model;

                        public class User {
                        }
                        """));

        ParseResult result = provider.parse(sources);

        assertThat(result.failures())
                .extracting(ParseFailure::path)
                .containsExactly("src/main/java/Broken.java");
        assertThat(result.declarations().byFqn("com.example.model.User")).isPresent();
    }

    @Test
    void skipsNonJavaSources() {
        ParseResult result = provider.parse(List.of(
                new SourceInput("README.md", "# not java")));

        assertThat(result.declarations().all()).isEmpty();
        assertThat(result.failures()).isEmpty();
    }
}
