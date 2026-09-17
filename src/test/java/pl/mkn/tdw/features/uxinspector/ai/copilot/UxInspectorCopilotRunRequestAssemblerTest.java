package pl.mkn.tdw.features.uxinspector.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportToolNames;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorPromptPreparationService;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorComponentSourcePackArtifact;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorComponentSourcePackArtifactService;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorRepositoryGuidanceArtifactService;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorRepositoryTreeArtifact;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorRepositoryTreeArtifactService;
import pl.mkn.tdw.features.uxinspector.ai.tools.UxInspectorTargetToolSetFactory;
import pl.mkn.tdw.features.uxinspector.ai.tools.UxInspectorToolNames;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStartRequest;
import pl.mkn.tdw.features.uxinspector.report.UxInspectorReportFactory;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorCopilotRunRequestAssemblerTest {

    @Test
    void shouldDisableSkillsAndRetainOnlyScopedNeutralSourceAndReportTools() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        when(toolFactory.createToolDefinitions(any(), any(), anyList())).thenReturn(registeredTools());
        var targetContext = targetContext();
        var request = new UxInspectorJobStartRequest(
                "crm-agent-portal", "main", VIEW_ID, REVISION, "Skad biora sie dane?",
                capture(), "gpt-5.6-terra", "medium"
        );
        var treeArtifactService = mock(UxInspectorRepositoryTreeArtifactService.class);
        when(treeArtifactService.prepare(any())).thenReturn(new UxInspectorRepositoryTreeArtifact(
                "complete: true\npaths:\n- [file] README.md", List.of("README.md")));
        var guidanceArtifactService = mock(UxInspectorRepositoryGuidanceArtifactService.class);
        when(guidanceArtifactService.render(any(), anyList())).thenReturn("""
                {"copilotInstructions":{"present":false},"projectSkills":[]}
                """);
        var componentPackService = mock(UxInspectorComponentSourcePackArtifactService.class);
        when(componentPackService.prepare(any(), any())).thenReturn(new UxInspectorComponentSourcePackArtifact(
                "componentCount: 1\ncomplete: true", 1, 1, 0, 2, 2, 0,
                java.util.Set.of(SOURCE_PATH, TEMPLATE_PATH)));
        var preparation = new UxInspectorPromptPreparationService(
                new ObjectMapper().findAndRegisterModules(), treeArtifactService, guidanceArtifactService,
                componentPackService)
                .prepare(request, targetContext);
        var assembler = new UxInspectorCopilotRunRequestAssembler(
                toolFactory,
                new UxInspectorCopilotToolSessionContextFactory(),
                new UxInspectorTargetToolSetFactory(),
                new CopilotRunAuthMapper(),
                new UxInspectorReportFactory()
        );

        var assembly = assembler.assemble(
                "crm-ux-run", request, targetContext, preparation, AnalysisAiAuthRef.localToken(null)
        );
        var session = assembly.runRequest().sessionConfigRequest();

        assertThat(session.skillsEnabled()).isFalse();
        assertThat(session.effectiveAvailableToolNames())
                .contains(GitLabToolNames.LIST_REPOSITORY_TREE, GitLabToolNames.LIST_REPOSITORY_FILES,
                        GitLabToolNames.SEARCH_REPOSITORY_FILES, GitLabToolNames.READ_REPOSITORY_FILE,
                        GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                        GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE)
                .containsAll(CopilotReportToolNames.allToolNames())
                .doesNotContain("skill", GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                        GitLabToolNames.LIST_REPOSITORY_BRANCHES);
        assertThat(assembly.toolAccessPolicy().sourceToolsAvailable()).isTrue();
        assertThat(assembly.toolAccessPolicy().reportToolsAvailable()).isTrue();
        assertThat(assembly.repositoryToolScope().selectedProject()).isEqualTo("crm-ui");
        assertThat(assembly.repositoryToolScope().selectedBranch()).isEqualTo("main");
        assertThat(assembly.repositoryToolScope().selectedCommit()).isEqualTo(REVISION);
        assertThat(assembly.runRequest().prompt()).contains("sourceToolScope", "W jednym turnie wywolaj rownolegle");
    }

    private List<ToolDefinition> registeredTools() {
        var names = new ArrayList<>(List.of(
                UxInspectorToolNames.LIST_TARGET_CANDIDATES,
                UxInspectorToolNames.READ_TARGET_SLICE,
                GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                GitLabToolNames.LIST_REPOSITORY_TREE,
                GitLabToolNames.LIST_REPOSITORY_FILES,
                GitLabToolNames.SEARCH_REPOSITORY_FILES,
                GitLabToolNames.READ_REPOSITORY_FILE,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE,
                GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                "skill"
        ));
        names.addAll(CopilotReportToolNames.allToolNames());
        return names.stream().map(this::tool).toList();
    }

    private ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(
                name, name, Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
    }
}
