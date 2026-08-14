package pl.mkn.tdw.api.aiskills;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void shouldExposeEditableRuntimeCatalogWithoutCaching() throws Exception {
        when(skillCatalogService.catalog()).thenReturn(new AiSkillCatalogResponse(
                "ai-skills.catalog",
                2,
                "EDITABLE",
                "COPILOT_RUNTIME",
                1,
                1,
                0,
                List.of(new AiSkillSummaryResponse(
                        "incident-analysis-orchestrator",
                        "Coordinates incident analysis.",
                        120,
                        "DEFAULT",
                        true
                ))
        ));

        mockMvc.perform(get("/api/ai/skills"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.contract").value("ai-skills.catalog"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.mode").value("EDITABLE"))
                .andExpect(jsonPath("$.customSkillCount").value(0))
                .andExpect(jsonPath("$.skills[0].state").value("DEFAULT"));
    }

    @Test
    void shouldExposeRenderedAndRawSkillContent() throws Exception {
        when(skillCatalogService.detail("incident-analysis-orchestrator")).thenReturn(detail("DEFAULT"));

        mockMvc.perform(get("/api/ai/skills/incident-analysis-orchestrator"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.contract").value("ai-skills.detail"))
                .andExpect(jsonPath("$.state").value("DEFAULT"))
                .andExpect(jsonPath("$.markdown").value("# Runtime guidance"));
    }

    @Test
    void shouldUpdateSkillAndRestoreDefault() throws Exception {
        when(skillCatalogService.update(any(), any())).thenReturn(detail("CUSTOM"));
        when(skillCatalogService.restoreDefault("incident-analysis-orchestrator")).thenReturn(detail("DEFAULT"));

        mockMvc.perform(put("/api/ai/skills/incident-analysis-orchestrator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rawMarkdown":"---\\nname: incident-analysis-orchestrator\\ndescription: Updated.\\n---\\n\\n# Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.state").value("CUSTOM"));

        mockMvc.perform(post("/api/ai/skills/incident-analysis-orchestrator/restore-default"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.state").value("DEFAULT"));
    }

    @Test
    void shouldReturnControlledNotFoundForUnknownSkill() throws Exception {
        when(skillCatalogService.detail("missing-skill"))
                .thenThrow(new AiSkillNotFoundException("missing-skill"));

        mockMvc.perform(get("/api/ai/skills/missing-skill"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_SKILL_NOT_FOUND"));
    }

    @Test
    void shouldReturnUnprocessableEntityForInvalidSkillContent() throws Exception {
        when(skillCatalogService.update(any(), any())).thenThrow(new AiSkillCatalogMutationException(
                "AI_SKILL_VALIDATION_FAILED",
                UserFacingErrorType.UNPROCESSABLE_ENTITY,
                "AI skill content failed validation."
        ));

        mockMvc.perform(put("/api/ai/skills/incident-analysis-orchestrator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawMarkdown\":\"broken\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AI_SKILL_VALIDATION_FAILED"));
    }

    private AiSkillDetailResponse detail(String state) {
        return new AiSkillDetailResponse(
                "ai-skills.detail",
                2,
                "EDITABLE",
                "COPILOT_RUNTIME",
                "incident-analysis-orchestrator",
                "Coordinates incident analysis.",
                120,
                "# Runtime guidance",
                "---\nname: incident-analysis-orchestrator\ndescription: Coordinates incident analysis.\n---\n\n# Runtime guidance",
                state,
                true
        );
    }
}
