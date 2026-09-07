package com.evoreview.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LlmHealthClient {

    private final String healthUrl;
    private final HttpClient httpClient;

    public LlmHealthClient(@Value("${evoreview.llm-service.base-url}") String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.healthUrl = normalized + "/health";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public Map<String, String> health() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("service", "evoreview-llm-service");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && response.body().contains("\"status\":\"UP\"")) {
                result.put("status", "UP");
            } else {
                result.put("status", "DOWN");
            }
        } catch (Exception ex) {
            result.put("status", "DOWN");
        }
        return result;
    }
}
