package pl.mkn.tdw.api.aiskills;

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
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillCatalogResponse;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillDetailResponse;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillSummaryResponse;

@WebMvcTest(AiSkillCatalogController.class)
class AiSkillCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiSkillCatalogService skillCatalogService;

    @Test
    void shouldExposeReadOnlyRuntimeCatalog() throws Exception {
        when(skillCatalogService.catalog()).thenReturn(new AiSkillCatalogResponse(
                "ai-skills.catalog",
                1,
                "READ_ONLY",
                "COPILOT_RUNTIME",
                1,
                List.of(new AiSkillSummaryResponse(
                        "incident-analysis-orchestrator",
                        "Coordinates incident analysis.",
                        120
                ))
        ));

        mockMvc.perform(get("/api/ai/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("ai-skills.catalog"))
                .andExpect(jsonPath("$.mode").value("READ_ONLY"))
                .andExpect(jsonPath("$.source").value("COPILOT_RUNTIME"))
                .andExpect(jsonPath("$.skillCount").value(1))
                .andExpect(jsonPath("$.skills[0].name").value("incident-analysis-orchestrator"))
                .andExpect(jsonPath("$.skills[0].lineCount").value(120));
    }

    @Test
    void shouldExposeRenderedAndRawSkillContent() throws Exception {
        when(skillCatalogService.detail("incident-analysis-orchestrator"))
                .thenReturn(new AiSkillDetailResponse(
                        "ai-skills.detail",
                        1,
                        "READ_ONLY",
                        "COPILOT_RUNTIME",
                        "incident-analysis-orchestrator",
                        "Coordinates incident analysis.",
                        120,
                        "# Runtime guidance",
                        "---\nname: incident-analysis-orchestrator\n---\n\n# Runtime guidance"
                ));

        mockMvc.perform(get("/api/ai/skills/incident-analysis-orchestrator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("ai-skills.detail"))
                .andExpect(jsonPath("$.name").value("incident-analysis-orchestrator"))
                .andExpect(jsonPath("$.markdown").value("# Runtime guidance"))
                .andExpect(jsonPath("$.rawMarkdown").value(
                        "---\nname: incident-analysis-orchestrator\n---\n\n# Runtime guidance"
                ));
    }

    @Test
    void shouldReturnControlledNotFoundForUnknownSkill() throws Exception {
        when(skillCatalogService.detail("missing-skill"))
                .thenThrow(new AiSkillNotFoundException("missing-skill"));

        mockMvc.perform(get("/api/ai/skills/missing-skill"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_SKILL_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("AI skill not found: missing-skill"));
    }
}
