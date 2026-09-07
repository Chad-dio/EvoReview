package com.evoreview.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class StackController {

    private final LlmHealthClient llmHealthClient;

    public StackController(LlmHealthClient llmHealthClient) {
        this.llmHealthClient = llmHealthClient;
    }

    @GetMapping("/api/stack")
    public Map<String, Object> stack() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("backend", Map.of(
                "service", "evoreview-backend",
                "status", "UP"
        ));
        body.put("llmService", llmHealthClient.health());
        return body;
    }
}
