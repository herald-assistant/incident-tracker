package pl.mkn.tdw.features.uxinspector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStartRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorPromptAndSkillsTest {
    private final UxInspectorRepositoryTreeArtifactService repositoryTreeArtifactService =
            mock(UxInspectorRepositoryTreeArtifactService.class);
    private final UxInspectorRepositoryGuidanceArtifactService repositoryGuidanceArtifactService =
            mock(UxInspectorRepositoryGuidanceArtifactService.class);
    private final UxInspectorPromptPreparationService service =
            new UxInspectorPromptPreparationService(
                    new ObjectMapper().findAndRegisterModules(), repositoryTreeArtifactService,
                    repositoryGuidanceArtifactService);

    @BeforeEach
    void setUp() {
        when(repositoryTreeArtifactService.prepare(any())).thenReturn(new UxInspectorRepositoryTreeArtifact("""
                repository: CRM/crm-ui
                branch: main
                commit: %s
                depth: 4
                content: PATH_NAMES_ONLY
                complete: true
                paths:
                - [dir] .github
                - [file] .github/copilot-instructions.md
                - [file] README.md
                - [dir] src
                """.formatted(REVISION), java.util.List.of(
                ".github/copilot-instructions.md", ".github/skills/crm-architecture/SKILL.md", "README.md")));
        when(repositoryGuidanceArtifactService.render(any(), any())).thenReturn("""
                {
                  "schema" : "tdw.ux-inspector-repository-guidance",
                  "version" : 1,
                  "repository" : "CRM/crm-ui",
                  "commit" : "%s",
                  "copilotInstructions" : {
                    "path" : ".github/copilot-instructions.md",
                    "present" : true,
                    "content" : "Search shared CRM guards before concluding."
                  },
                  "projectSkills" : [ {
                    "path" : ".github/skills/crm-architecture/SKILL.md",
                    "name" : "crm-architecture",
                    "description" : "Explains cross-cutting CRM architecture."
                  } ]
                }
                """.formatted(REVISION));
    }

    @Test
    void shouldKeepTheCompleteAdaptiveProcedureInThePromptWithoutFeatureSkills() {
        var preparation = service.prepare(request("Dlaczego przycisk jest zablokowany?"), targetContext());

        assertThat(preparation.prompt())
                .contains("UNTRUSTED_RUNTIME_OBSERVATION", "UNTRUSTED_SOURCE_EVIDENCE", "pinned revision")
                .contains("czy pytanie jest precyzyjne, czy ogolne")
                .contains("Dla pytania precyzyjnego", "Dla pytania ogolnego")
                .contains("gitlab_list_repository_tree", "gitlab_list_repository_files",
                        "gitlab_search_repository_files", "gitlab_read_repository_file",
                        "gitlab_read_repository_file_chunk")
                .contains("sourceToolScope", "projectName: crm-ui", "branchRef: main",
                        "pinnedCommit: " + REVISION)
                .contains("DETERMINISTIC_SOURCE_BINDING", "sourceReference", "Selector jest tylko sygnalem lokalizacji")
                .contains("formSnapshot", "zamrozona obserwacja runtime")
                .contains("README", "AGENTS.md", ".github/copilot-instructions.md", "complete: true")
                .contains("Search shared CRM guards before concluding.", "crm-architecture",
                        "Explains cross-cutting CRM architecture.")
                .contains("MUSISZ odczytac", "gitlab_read_repository_file", "mechanizmy",
                        "guards", "interceptory", "initializery", "feature flags")
                .contains("report_update_header", "report_upsert_section", "report_update_meta", "report_get_current")
                .contains("W jednym turnie wywolaj rownolegle")
                .contains("jedynej sekcji `answer`")
                .doesNotContain("pathPrefixes")
                .doesNotContain("ui-explorer-")
                .doesNotContain("ux-inspector-orchestrator")
                .doesNotContain("ux-inspector-validation")
                .doesNotContain("osiem sekcji");
        assertThat(preparation.artifactContents()).containsKeys(
                UxInspectorPromptPreparationService.CAPTURE_ARTIFACT,
                UxInspectorPromptPreparationService.TARGET_ARTIFACT,
                UxInspectorPromptPreparationService.REPOSITORY_TREE_ARTIFACT,
                UxInspectorPromptPreparationService.REPOSITORY_GUIDANCE_ARTIFACT,
                UxInspectorPromptPreparationService.REPORT_ARTIFACT);
    }

    @Test
    void shouldSupportAGeneralQuestionWithoutGuessingAKeywordSpecificWorkflow() {
        var preparation = service.prepare(request("Opisz ten element funkcjonalnie."), targetContext());

        assertThat(preparation.prompt())
                .contains("cel biznesowy", "skad pochodza wyswietlane lub wpisywane dane",
                        "biznesowe i techniczne obowiazuja", "gdzie dane sa przekazywane albo")
                .contains("elementu przez binding, komponent, stan, serwis, klienta lub persistence")
                .doesNotContain("specialistSkill");
    }

    private UxInspectorJobStartRequest request(String question) {
        return new UxInspectorJobStartRequest("crm-agent-portal", "main", VIEW_ID, REVISION,
                question, capture(), "gpt-crm", "medium");
    }
}
