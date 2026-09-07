package com.evoreview.context.parse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveFileClassifierTest {

    private final SensitiveFileClassifier classifier = SensitiveFileClassifier.withDefaults();

    @ParameterizedTest
    @ValueSource(strings = {
            ".env",
            ".env.local",
            "backend/.env.production",
            "github-app.pem",
            "certs/server.key",
            "keystore.p12",
            "truststore.jks",
            "credentials.json",
            "config/secrets.yaml"
    })
    void flagsSensitiveFiles(String path) {
        assertThat(classifier.isSensitive(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "src/main/java/Foo.java",
            "docs/keys-guide.md",
            "pem.txt",
            "src/environment.ts",
            "README.md"
    })
    void allowsNormalFiles(String path) {
        assertThat(classifier.isSensitive(path)).isFalse();
    }
}
