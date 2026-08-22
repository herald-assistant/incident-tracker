package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotContextTierPreference;
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
                GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                GitLabToolNames.READ_REPOSITORY_FILE,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK
        );
        assertThat(assembly.runRequest().sessionConfigRequest().effectiveAvailableToolNames()).contains("skill");
        assertThat(assembly.runRequest().sessionConfigRequest().availableToolNames())
                .doesNotContain("gitlab_list_available_repositories", "db_describe_table");
        assertThat(assembly.runRequest().sessionConfigRequest().contextTierPreference())
                .isEqualTo(CopilotContextTierPreference.LONG_CONTEXT_REQUIRED);
        assertThat(assembly.runRequest().artifactContents()).hasSize(7);
        assertThat(assembly.runRequest().artifactContents())
                .containsKey("ui-explorer/functional-writing-contract.md");
        var durableInstructions = assembly.runRequest().sessionConfigRequest().durableSystemInstructions();
        assertThat(durableInstructions)
                .contains("`screen.screenId` jest wymagane")
                .contains("top-level `screenId` jest zabronione")
                .contains("`sourceRevision` musi byc obiektem")
                .contains(preparation.artifactContents().get(UiExplorerArtifactService.SCREEN_CATALOG_ENTRY_ARTIFACT))
                .contains(preparation.artifactContents().get(UiExplorerArtifactService.RESPONSE_CONTRACT_ARTIFACT))
                .doesNotContain(preparation.artifactContents().get(UiExplorerArtifactService.SOURCE_SLICES_ARTIFACT));
        assertThat(durableInstructions.length())
                .as("durable contract must not duplicate the source research context")
                .isLessThan(10_000);
        var hidden = contextCaptor.getValue().hiddenContext();
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_GROUP)).isEqualTo("synthetic-crm");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_BRANCH)).isEqualTo("main");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES))
                .isEqualTo(List.of("crm-agent-portal"));
        assertThat(hidden.get(AgentToolContextKeys.TOOL_BUDGET_POLICY))
                .isEqualTo(AgentToolContextKeys.TOOL_BUDGET_POLICY_GOAL_DRIVEN);
        assertThat(hidden.get(GitLabFrontendToolContextKeys.PROJECT_NAME)).isEqualTo("crm-agent-portal");
        assertThat(hidden.get(GitLabFrontendToolContextKeys.PATH_PREFIXES))
                .isEqualTo(List.of("apps/crm-agent"));
        assertThat(hidden.get(GitLabFrontendToolContextKeys.SOURCE_REVISION))
                .isEqualTo("crm-commit-abc123");
        assertThat(hidden.get(GitLabFrontendToolContextKeys.SCREEN_SLICE_REF))
                .isEqualTo("crm-contact-preferences");
        var sliceTargets = (Map<?, ?>) hidden.get(GitLabFrontendToolContextKeys.TYPESCRIPT_SLICE_TARGETS);
        org.junit.jupiter.api.Assertions.assertTrue(sliceTargets.containsKey("component-crm-contact-preferences"));
        org.junit.jupiter.api.Assertions.assertTrue(sliceTargets.containsKey("dependency-crm-preferences-api"));
        assertThat(preparation.prompt()).doesNotContain("synthetic-crm");
    }

    private List<ToolDefinition> registeredTools() {
        return List.of(
                tool(GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE),
                tool(GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE),
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
