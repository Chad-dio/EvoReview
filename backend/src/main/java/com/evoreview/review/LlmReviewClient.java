package com.evoreview.review;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class LlmReviewClient {

    private final String reviewUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlmReviewClient(@Value("${evoreview.llm-service.base-url}") String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.reviewUrl = normalized + "/review";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public LlmReviewResponse review(ReviewRequest request) {
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(reviewUrl))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmReviewException("LLM service responded with status " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), LlmReviewResponse.class);
        } catch (LlmReviewException e) {
            throw e;
        } catch (IOException e) {
            throw new LlmReviewException("LLM review call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmReviewException("LLM review call interrupted", e);
        }
    }

    public record LlmReviewResponse(
            List<WireFinding> findings,
            String model,
            WireUsage usage,
            Integer droppedFindings) {}

    public record WireFinding(
            String path,
            Integer line,
            String severity,
            String title,
            String message,
            String suggestion,
            List<String> citedItemIds) {}

    public record WireUsage(Integer promptTokens, Integer completionTokens) {}
}
