package com.evoreview.health;

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

    @Test
    void stackReturnsBackendAndLlmStatus() throws Exception {
        when(llmHealthClient.health()).thenReturn(Map.of(
                "service", "evoreview-llm-service",
                "status", "UP"
        ));

        mockMvc.perform(get("/api/stack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backend.service").value("evoreview-backend"))
                .andExpect(jsonPath("$.backend.status").value("UP"))
                .andExpect(jsonPath("$.llmService.service").value("evoreview-llm-service"))
                .andExpect(jsonPath("$.llmService.status").value("UP"));
    }
}
