package com.evoreview.context.semantic;

import java.util.List;

/**
 * In-memory mini repository covering the cases the semantic layer must handle:
 * overloads, same-name methods on unrelated types, receiver-name vs import-based
 * disambiguation, interface/implementation pairs, and a test-source-set caller.
 */
public final class FixtureRepo {

    public static final String USER = "src/main/java/com/example/model/User.java";
    public static final String FOO_SERVICE = "src/main/java/com/example/service/FooService.java";
    public static final String FOO_CALLER = "src/main/java/com/example/service/FooCaller.java";
    public static final String ORDER_CALLER = "src/main/java/com/example/order/OrderCaller.java";
    public static final String RETRY_POLICY = "src/main/java/com/example/policy/RetryPolicy.java";
    public static final String DEFAULT_RETRY_POLICY = "src/main/java/com/example/policy/DefaultRetryPolicy.java";
    public static final String FOO_SERVICE_TEST = "src/test/java/com/example/service/FooServiceTest.java";
    public static final String USER_REPOSITORY = "src/main/java/com/example/repo/UserRepository.java";
    public static final String REPO_CALLER = "src/main/java/com/example/repo/RepoCaller.java";

    private FixtureRepo() {
    }

    public static List<SourceInput> sources() {
        return List.of(
                new SourceInput(USER, """
                        package com.example.model;

                        public class User {
                            private String name;

                            public String getName() {
                                return name;
                            }
                        }
                        """),
                new SourceInput(FOO_SERVICE, """
                        package com.example.service;

                        import com.example.model.User;

                        public class FooService {

                            public void save(User user) {
                                validate(user);
                            }

                            public void save(User user, boolean strict) {
                                validate(user);
                            }

                            private void validate(User user) {
                            }
                        }
                        """),
                new SourceInput(FOO_CALLER, """
                        package com.example.service;

                        import com.example.model.User;

                        public class FooCaller {

                            private FooService fooService;

                            public void handle(User user) {
                                fooService.save(user);
                            }
                        }
                        """),
                new SourceInput(ORDER_CALLER, """
                        package com.example.order;

                        import com.example.model.User;
                        import com.example.service.FooService;

                        public class OrderCaller {

                            private FooService service;

                            public void place(User user) {
                                service.save(user);
                            }
                        }
                        """),
                new SourceInput(RETRY_POLICY, """
                        package com.example.policy;

                        public interface RetryPolicy {
                            void retry(int attempts);
                        }
                        """),
                new SourceInput(DEFAULT_RETRY_POLICY, """
                        package com.example.policy;

                        public class DefaultRetryPolicy implements RetryPolicy {

                            @Override
                            public void retry(int attempts) {
                            }
                        }
                        """),
                new SourceInput(FOO_SERVICE_TEST, """
                        package com.example.service;

                        import com.example.model.User;

                        class FooServiceTest {

                            private FooService fooService;

                            void saves() {
                                fooService.save(new User());
                            }
                        }
                        """),
                new SourceInput(USER_REPOSITORY, """
                        package com.example.repo;

                        import com.example.model.User;

                        public class UserRepository {

                            public void save(User user) {
                            }
                        }
                        """),
                new SourceInput(REPO_CALLER, """
                        package com.example.repo;

                        import com.example.model.User;

                        public class RepoCaller {

                            private UserRepository userRepository;

                            public void handle(User user) {
                                userRepository.save(user);
                            }
                        }
                        """)
        );
    }

    public static List<String> headFiles() {
        return sources().stream().map(SourceInput::path).toList();
    }

    public static String contentOf(String path) {
        return sources().stream()
                .filter(source -> source.path().equals(path))
                .findFirst()
                .orElseThrow()
                .content();
    }
}
