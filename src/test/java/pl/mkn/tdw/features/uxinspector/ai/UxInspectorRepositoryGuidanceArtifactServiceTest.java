package pl.mkn.tdw.features.uxinspector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorContextException;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.REVISION;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.targetContext;

class UxInspectorRepositoryGuidanceArtifactServiceTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final UxInspectorRepositoryGuidanceArtifactService service =
            new UxInspectorRepositoryGuidanceArtifactService(
                    repositoryPort, new ObjectMapper().findAndRegisterModules());

    @Test
    void shouldInlinePinnedCopilotInstructionsAndOnlyCatalogHeadersFromSupportedProjectSkills() {
        var copilot = ".github/copilot-instructions.md";
        var githubSkill = ".github/skills/frontend-architecture/SKILL.md";
        var claudeSkill = ".claude/skills/data-flow/SKILL.md";
        var agentsSkill = ".agents/skills/access-control/SKILL.md";
        stubFile(copilot, "Search shared guards and initializers before answering.");
        stubFile(githubSkill, skill("frontend-architecture", "Explains global frontend mechanisms.",
                "PRIVATE GITHUB SKILL BODY"));
        stubFile(claudeSkill, skill("data-flow", "Traces data from UI to persistence.",
                "PRIVATE CLAUDE SKILL BODY"));
        stubFile(agentsSkill, skill("access-control", "Finds guards and authorization policy.",
                "PRIVATE AGENTS SKILL BODY"));

        var artifact = service.render(targetContext(), List.of(
                "docs/examples/SKILL.md", agentsSkill, copilot, githubSkill, claudeSkill));

        assertThat(artifact)
                .contains("tdw.ux-inspector-repository-guidance", REVISION,
                        "Search shared guards and initializers before answering.",
                        "frontend-architecture", "Explains global frontend mechanisms.",
                        "data-flow", "Traces data from UI to persistence.",
                        "access-control", "Finds guards and authorization policy.")
                .doesNotContain("PRIVATE GITHUB SKILL BODY", "PRIVATE CLAUDE SKILL BODY",
                        "PRIVATE AGENTS SKILL BODY", "docs/examples/SKILL.md");
        verify(repositoryPort, times(4)).readFileMetadata(eq("CRM"), eq("crm-ui"), eq(REVISION), anyString());
        verify(repositoryPort, times(4)).readFileBounded(
                eq("CRM"), eq("crm-ui"), eq(REVISION), anyString(), anyInt());
    }

    @Test
    void shouldRepresentAnAbsentOptionalInstructionsFileWithoutTryingToReadIt() {
        var artifact = service.render(targetContext(), List.of("README.md", "src/app/app.ts"));

        assertThat(artifact).contains("\"path\" : \".github/copilot-instructions.md\"",
                "\"present\" : false", "\"projectSkills\" : [ ]");
        verifyNoInteractions(repositoryPort);
    }

    @Test
    void shouldFailClosedWhenADiscoveredSkillDoesNotHaveAUsableHeader() {
        var path = ".github/skills/frontend-architecture/SKILL.md";
        stubFile(path, "# Missing frontmatter\nArchitecture notes.");

        assertThatThrownBy(() -> service.render(targetContext(), List.of(path)))
                .isInstanceOfSatisfying(UxInspectorContextException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("UX_INSPECTOR_REPOSITORY_GUIDANCE_INVALID");
                    assertThat(exception.getMessage()).contains("missing YAML frontmatter", path);
                });
    }

    @Test
    void shouldFailClosedWhenInstructionsListedAtThePinnedCommitCannotBeVerified() {
        var path = ".github/copilot-instructions.md";
        when(repositoryPort.readFileMetadata("CRM", "crm-ui", REVISION, path)).thenReturn(null);

        assertThatThrownBy(() -> service.render(targetContext(), List.of(path)))
                .isInstanceOfSatisfying(UxInspectorContextException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("UX_INSPECTOR_REPOSITORY_GUIDANCE_UNAVAILABLE");
                    assertThat(exception.getMessage()).contains("pinned revision", path);
                });
    }

    private String skill(String name, String description, String body) {
        return "---\nname: " + name + "\ndescription: " + description + "\n---\n\n" + body + "\n";
    }

    private void stubFile(String path, String content) {
        var size = (long) content.getBytes(StandardCharsets.UTF_8).length;
        when(repositoryPort.readFileMetadata("CRM", "crm-ui", REVISION, path)).thenReturn(
                new GitLabRepositoryFileMetadata("CRM", "crm-ui", REVISION, path,
                        "blob-" + path, REVISION, REVISION, null, null, size));
        when(repositoryPort.readFileBounded(eq("CRM"), eq("crm-ui"), eq(REVISION), eq(path), anyInt()))
                .thenReturn(new GitLabRepositoryFileContent(
                        "CRM", "crm-ui", REVISION, path, content, false));
    }
}
