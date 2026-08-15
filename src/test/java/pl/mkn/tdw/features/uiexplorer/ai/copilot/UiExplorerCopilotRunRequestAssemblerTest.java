package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerArtifactService;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparationService;
import pl.mkn.tdw.features.uiexplorer.ai.readiness.UiExplorerAiReadinessGate;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerCopilotRunRequestAssemblerTest {

    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("ui-explorer");

    @Test
    void shouldAssemblePartialCrmRunWithOnlyScopedFallbackToolsAndSkill() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var contextCaptor = ArgumentCaptor.forClass(CopilotToolSessionContext.class);
        when(toolFactory.createToolDefinitions(contextCaptor.capture(), eq(DESCRIPTION_CONTEXT)))
                .thenReturn(registeredTools());
        var preparation = new UiExplorerPromptPreparationService(
                new UiExplorerArtifactService(new ObjectMapper()),
                new CopilotArtifactContentMapper()
        ).prepare(request(), context());
        var readiness = new UiExplorerAiReadinessGate().evaluate(request(), context());
        var assembler = new UiExplorerCopilotRunRequestAssembler(
                toolFactory,
                new UiExplorerCopilotToolSessionContextFactory(),
                new CopilotRunAuthMapper()
        );

        var assembly = assembler.assemble(
                "crm-ui-run-1",
                request(),
                context(),
                preparation,
                readiness,
                AnalysisAiAuthRef.localToken(null)
        );

        assertThat(assembly.toolAccessPolicy().availableToolNames()).containsExactlyInAnyOrder(
                GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                GitLabToolNames.READ_REPOSITORY_FILE,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK
        );
        assertThat(assembly.runRequest().sessionConfigRequest().effectiveAvailableToolNames()).contains("skill");
        assertThat(assembly.runRequest().sessionConfigRequest().availableToolNames())
                .doesNotContain("gitlab_list_available_repositories", "db_describe_table");
        assertThat(assembly.runRequest().artifactContents()).hasSize(6);
        var hidden = contextCaptor.getValue().hiddenContext();
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_GROUP)).isEqualTo("synthetic-crm");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_BRANCH)).isEqualTo("main");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES))
                .isEqualTo(List.of("crm-agent-portal"));
        assertThat(hidden.get(UiExplorerCopilotToolContextKeys.ALLOWED_REPOSITORY).toString())
                .contains("crm-agent-portal", "apps/crm-agent", "main");
        assertThat(preparation.prompt()).doesNotContain("synthetic-crm");
    }

    private List<ToolDefinition> registeredTools() {
        return List.of(
                tool(GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES),
                tool(GitLabToolNames.READ_REPOSITORY_FILE),
                tool(GitLabToolNames.READ_REPOSITORY_FILE_CHUNK),
                tool(GitLabToolNames.LIST_AVAILABLE_REPOSITORIES),
                tool("db_describe_table")
        );
    }

    private ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(
                name,
                name,
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
    }
}
