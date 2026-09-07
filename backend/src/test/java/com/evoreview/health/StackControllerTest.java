package com.evoreview.health;

import com.evoreview.github.GitHubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StackController.class)
class StackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LlmHealthClient llmHealthClient;

    @MockBean
    private GitHubProperties gitHubProperties;

    @Test
    void stackReturnsBackendAndLlmStatus() throws Exception {
        when(llmHealthClient.health()).thenReturn(Map.of(
                "service", "evoreview-llm-service",
                "status", "UP"
        ));
        when(gitHubProperties.isConfigured()).thenReturn(false);
        when(gitHubProperties.getClientId()).thenReturn("Iv23liwrmMSa20qTj7hv");
        when(gitHubProperties.webhookUrl()).thenReturn("http://t6436638.natappfree.cc/api/github/webhook");

        mockMvc.perform(get("/api/stack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backend.service").value("evoreview-backend"))
                .andExpect(jsonPath("$.backend.status").value("UP"))
                .andExpect(jsonPath("$.llmService.service").value("evoreview-llm-service"))
                .andExpect(jsonPath("$.llmService.status").value("UP"))
                .andExpect(jsonPath("$.github.configured").value(false))
                .andExpect(jsonPath("$.github.clientIdConfigured").value(true))
                .andExpect(jsonPath("$.github.webhookUrl").value("http://t6436638.natappfree.cc/api/github/webhook"));
    }
}
