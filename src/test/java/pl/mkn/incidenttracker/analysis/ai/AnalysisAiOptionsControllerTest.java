package pl.mkn.incidenttracker.analysis.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisAiOptionsController.class)
class AnalysisAiOptionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisAiModelOptionsProvider modelOptionsProvider;

    @Test
    void shouldExposeAiModelOptions() throws Exception {
        when(modelOptionsProvider.modelOptions()).thenReturn(new AnalysisAiModelOptionsResponse(
                "gpt-5.4",
                "medium",
                List.of("low", "medium", "high"),
                List.of(
                        new AnalysisAiModelOption(
                                "gpt-5.4",
                                "GPT-5.4",
                                true,
                                List.of("low", "medium", "high"),
                                "medium"
                        ),
                        new AnalysisAiModelOption(
                                "gpt-5.4-mini",
                                "GPT-5.4 Mini",
                                false,
                                List.of(),
                                null
                        )
                )
        ));

        mockMvc.perform(get("/analysis/ai/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultModel").value("gpt-5.4"))
                .andExpect(jsonPath("$.defaultReasoningEffort").value("medium"))
                .andExpect(jsonPath("$.defaultReasoningEfforts[0]").value("low"))
                .andExpect(jsonPath("$.models[0].id").value("gpt-5.4"))
                .andExpect(jsonPath("$.models[0].supportsReasoningEffort").value(true))
                .andExpect(jsonPath("$.models[0].reasoningEfforts[2]").value("high"))
                .andExpect(jsonPath("$.models[1].supportsReasoningEffort").value(false));
    }
}
