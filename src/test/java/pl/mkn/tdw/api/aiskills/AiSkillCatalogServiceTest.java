package pl.mkn.tdw.api.aiskills;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkill;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkillState;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillCatalogException;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillUpdateRequest;

class AiSkillCatalogServiceTest {

    private final CopilotSkillRuntimeLoader skillRuntimeLoader = mock(CopilotSkillRuntimeLoader.class);
    private final AiSkillCatalogService service = new AiSkillCatalogService(skillRuntimeLoader);

    @Test
    void shouldProjectEditableRuntimeSkillsWithoutStoragePaths() {
        when(skillRuntimeLoader.availableSkills()).thenReturn(List.of(
                skill("incident-analysis-orchestrator", CopilotRuntimeSkillState.DEFAULT, true),
                skill("local-only-skill", CopilotRuntimeSkillState.CUSTOM, false)
        ));

        var response = service.catalog();

        assertThat(response.contract()).isEqualTo("ai-skills.catalog");
        assertThat(response.version()).isEqualTo(2);
        assertThat(response.mode()).isEqualTo("EDITABLE");
        assertThat(response.source()).isEqualTo("COPILOT_RUNTIME");
        assertThat(response.skillCount()).isEqualTo(2);
        assertThat(response.defaultSkillCount()).isEqualTo(1);
        assertThat(response.customSkillCount()).isEqualTo(1);
        assertThat(response.skills()).first().satisfies(skill -> {
            assertThat(skill.name()).isEqualTo("incident-analysis-orchestrator");
            assertThat(skill.description()).isEqualTo("Coordinates incident analysis.");
            assertThat(skill.lineCount()).isEqualTo(8);
            assertThat(skill.state()).isEqualTo("DEFAULT");
            assertThat(skill.restoreAvailable()).isTrue();
        });
    }

    @Test
    void shouldReturnExactRuntimeSkillDetail() {
        when(skillRuntimeLoader.availableSkills()).thenReturn(List.of(
                skill("incident-analysis-orchestrator", CopilotRuntimeSkillState.DEFAULT, true),
                skill("flow-explorer-orchestrator", CopilotRuntimeSkillState.CUSTOM, true)
        ));

        var response = service.detail("flow-explorer-orchestrator");

        assertThat(response.contract()).isEqualTo("ai-skills.detail");
        assertThat(response.version()).isEqualTo(2);
        assertThat(response.name()).isEqualTo("flow-explorer-orchestrator");
        assertThat(response.markdown()).isEqualTo("# Runtime guidance");
        assertThat(response.rawMarkdown()).startsWith("---\n");
        assertThat(response.state()).isEqualTo("CUSTOM");
    }

    @Test
    void shouldUpdateAndRestoreRuntimeSkill() {
        var custom = skill("incident-analysis-orchestrator", CopilotRuntimeSkillState.CUSTOM, true);
        var restored = skill("incident-analysis-orchestrator", CopilotRuntimeSkillState.DEFAULT, true);
        when(skillRuntimeLoader.updateSkill(custom.name(), custom.rawMarkdown())).thenReturn(custom);
        when(skillRuntimeLoader.restoreDefault(custom.name())).thenReturn(restored);

        assertThat(service.update(custom.name(), new AiSkillUpdateRequest(custom.rawMarkdown())).state())
                .isEqualTo("CUSTOM");
        assertThat(service.restoreDefault(custom.name()).state()).isEqualTo("DEFAULT");
    }

    @Test
    void shouldMapPlatformMutationFailuresToUserFacingErrors() {
        when(skillRuntimeLoader.updateSkill("known-skill", "broken"))
                .thenThrow(new CopilotSkillCatalogException(
                        CopilotSkillCatalogException.Code.INVALID_CONTENT,
                        "AI skill content failed validation."
                ));

        assertThatThrownBy(() -> service.update("known-skill", new AiSkillUpdateRequest("broken")))
                .isInstanceOf(AiSkillCatalogMutationException.class)
                .satisfies(exception -> assertThat(((AiSkillCatalogMutationException) exception).code())
                        .isEqualTo("AI_SKILL_VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectUnknownSkillNameWithoutResolvingAPath() {
        when(skillRuntimeLoader.availableSkills()).thenReturn(List.of(
                skill("known-skill", CopilotRuntimeSkillState.DEFAULT, true)
        ));

        assertThatThrownBy(() -> service.detail("../SKILL.md"))
                .isInstanceOf(AiSkillNotFoundException.class)
                .hasMessage("AI skill not found: ../SKILL.md");
    }

    private CopilotRuntimeSkill skill(
            String name,
            CopilotRuntimeSkillState state,
            boolean restoreAvailable
    ) {
        return new CopilotRuntimeSkill(
                name,
                "Coordinates incident analysis.",
                8,
                "# Runtime guidance",
                "---\nname: %s\ndescription: Coordinates incident analysis.\n---\n\n# Runtime guidance"
                        .formatted(name),
                state,
                restoreAvailable
        );
    }
}
