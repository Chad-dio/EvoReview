package com.evoreview.health;

import com.evoreview.github.GitHubProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class StackController {

    private final LlmHealthClient llmHealthClient;
    private final GitHubProperties gitHubProperties;

    public StackController(LlmHealthClient llmHealthClient, GitHubProperties gitHubProperties) {
        this.llmHealthClient = llmHealthClient;
        this.gitHubProperties = gitHubProperties;
    }

    @GetMapping("/api/stack")
    public Map<String, Object> stack() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("backend", Map.of(
                "service", "evoreview-backend",
                "status", "UP"
        ));
        body.put("llmService", llmHealthClient.health());
        Map<String, Object> github = new LinkedHashMap<>();
        github.put("configured", gitHubProperties.isConfigured());
        github.put("clientIdConfigured", !gitHubProperties.getClientId().isBlank());
        github.put("webhookUrl", gitHubProperties.webhookUrl());
        body.put("github", github);
        return body;
    }
}
