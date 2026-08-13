package pl.mkn.tdw.api.aiskills;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkill;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSkillCatalogServiceTest {

    private final CopilotSkillRuntimeLoader skillRuntimeLoader = mock(CopilotSkillRuntimeLoader.class);
    private final AiSkillCatalogService service = new AiSkillCatalogService(skillRuntimeLoader);

    @Test
    void shouldProjectRuntimeSkillsWithoutStoragePaths() {
        when(skillRuntimeLoader.availableSkills()).thenReturn(List.of(skill("incident-analysis-orchestrator")));

        var response = service.catalog();

        assertThat(response.contract()).isEqualTo("ai-skills.catalog");
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.mode()).isEqualTo("READ_ONLY");
        assertThat(response.source()).isEqualTo("COPILOT_RUNTIME");
        assertThat(response.skillCount()).isEqualTo(1);
        assertThat(response.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.name()).isEqualTo("incident-analysis-orchestrator");
            assertThat(skill.description()).isEqualTo("Coordinates incident analysis.");
            assertThat(skill.lineCount()).isEqualTo(8);
        });
    }

    @Test
    void shouldReturnExactRuntimeSkillDetail() {
        when(skillRuntimeLoader.availableSkills()).thenReturn(List.of(
                skill("incident-analysis-orchestrator"),
                skill("flow-explorer-orchestrator")
        ));

        var response = service.detail("flow-explorer-orchestrator");

        assertThat(response.contract()).isEqualTo("ai-skills.detail");
        assertThat(response.name()).isEqualTo("flow-explorer-orchestrator");
        assertThat(response.markdown()).isEqualTo("# Runtime guidance");
        assertThat(response.rawMarkdown()).startsWith("---\n");
    }

    @Test
    void shouldRejectUnknownSkillNameWithoutResolvingAPath() {
        when(skillRuntimeLoader.availableSkills()).thenReturn(List.of(skill("known-skill")));

        assertThatThrownBy(() -> service.detail("../SKILL.md"))
                .isInstanceOf(AiSkillNotFoundException.class)
                .hasMessage("AI skill not found: ../SKILL.md");
    }

    private CopilotRuntimeSkill skill(String name) {
        return new CopilotRuntimeSkill(
                name,
                "Coordinates incident analysis.",
                8,
                "# Runtime guidance",
                "---\nname: %s\ndescription: Coordinates incident analysis.\n---\n\n# Runtime guidance"
                        .formatted(name)
        );
    }
}
