package pl.mkn.tdw.features.uiexplorer.ai.chat;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.features.uiexplorer.ai.copilot.UiExplorerCopilotToolSessionContextFactory;
import pl.mkn.tdw.features.uiexplorer.ai.copilot.UiExplorerCopilotToolContextKeys;
import pl.mkn.tdw.features.uiexplorer.ai.copilot.UiExplorerDurableSystemInstructions;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerFollowUpChatServiceTest {

    @Test
    void shouldResumePinnedSessionWithReadOnlyResearchMode() {
        var promptService = mock(UiExplorerFollowUpPromptService.class);
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var preparationService = mock(CopilotRunPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var prepared = mock(CopilotPreparedSession.class);
        var runCaptor = ArgumentCaptor.forClass(CopilotRunRequest.class);
        var chatRequest = new UiExplorerFollowUpChatRequest(
                "crm-ui-run-1", request(), context(),
                "Co dzieje sie po zapisie?", "crm-ui-session-1", AnalysisAiAuthRef.localToken(null)
        );
        when(promptService.prepare(chatRequest)).thenReturn("follow-up prompt");
        when(toolFactory.createToolDefinitions(any(), any())).thenReturn(researchTools());
        when(preparationService.prepare(runCaptor.capture())).thenReturn(prepared);
        when(executionGateway.execute(prepared))
                .thenReturn(new CopilotExecutionResult("System zapisuje preferencje klienta.", null,
                        "crm-ui-session-1"));
        var service = new UiExplorerFollowUpChatService(
                promptService,
                new UiExplorerCopilotToolSessionContextFactory(),
                toolFactory,
                new CopilotRunAuthMapper(),
                preparationService,
                executionGateway
        );

        var result = service.chat(
                chatRequest,
                AnalysisAiToolEvidenceListener.NO_OP,
                AnalysisAiActivityListener.NO_OP
        );

        assertThat(result.content()).isEqualTo("System zapisuje preferencje klienta.");
        var runRequest = runCaptor.getValue();
        assertThat(runRequest.sessionTarget())
                .isEqualTo(new CopilotSessionTarget(CopilotSessionTarget.Type.EXISTING, "crm-ui-session-1"));
        assertThat(runRequest.prompt()).isEqualTo("follow-up prompt");
        assertThat(runRequest.initialReport()).isNull();
        assertThat(runRequest.artifactContents()).isEmpty();
        assertThat(runRequest.sessionConfigRequest().durableSystemInstructions())
                .isEqualTo(UiExplorerDurableSystemInstructions.followUp())
                .contains("konkretny kontrakt API", "schemat bazy danych", "systemu zewnetrznego")
                .contains("Nie dodawaj szczegolow implementacji");
        assertThat(runRequest.sessionConfigRequest().availableToolNames()).containsExactlyInAnyOrder(
                GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                GitLabToolNames.READ_REPOSITORY_FILE,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK
        );
        var toolContextCaptor = ArgumentCaptor.forClass(
                pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext.class);
        verify(toolFactory).createToolDefinitions(toolContextCaptor.capture(), any());
        var hidden = toolContextCaptor.getValue().hiddenContext();
        assertThat(hidden.get(UiExplorerCopilotToolContextKeys.RUN_KIND))
                .isEqualTo(UiExplorerCopilotToolContextKeys.RUN_KIND_FOLLOW_UP);
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_BRANCH)).isEqualTo("crm-commit-abc123");
        assertThat(hidden).doesNotContainKeys(
                AgentToolContextKeys.REPORT_ID,
                AgentToolContextKeys.REPORT_FEATURE,
                AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS
        );
    }

    private static List<ToolDefinition> researchTools() {
        return List.of(
                tool(GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE),
                tool(GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE),
                tool(GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES),
                tool(GitLabToolNames.READ_REPOSITORY_FILE),
                tool(GitLabToolNames.READ_REPOSITORY_FILE_CHUNK)
        );
    }

    private static ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(
                name,
                name,
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
    }
}
