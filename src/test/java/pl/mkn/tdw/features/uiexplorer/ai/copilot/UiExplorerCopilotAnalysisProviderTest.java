package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiAnalysisStatus;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerArtifactService;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparation;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparationService;
import pl.mkn.tdw.features.uiexplorer.ai.readiness.UiExplorerAiReadinessGate;
import pl.mkn.tdw.features.uiexplorer.ai.response.UiExplorerAiResponseParser;
import pl.mkn.tdw.features.uiexplorer.report.DefaultUiExplorerResultReportAssembler;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.FETCHED_VALIDATOR_PATH;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.completeResponse;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.fetchedCodeEvidence;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerCopilotAnalysisProviderTest {

    @Test
    void shouldExecuteIsolatedCrmRuntimeAndAcceptOnlyCapturedToolSource() {
        var objectMapper = new ObjectMapper();
        var toolFactory = mock(CopilotSdkToolFactory.class);
        when(toolFactory.createToolDefinitions(any(), any())).thenReturn(registeredTools());
        var runPreparation = mock(CopilotRunPreparationService.class);
        when(runPreparation.prepare(any())).thenReturn(new CopilotPreparedSession(
                "crm-provider-run", null, null, null, "prepared CRM prompt", Map.of()
        ));
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var usage = new AnalysisAiUsage(100, 50, 10, 0, 160, 0.01, 200, 1,
                "gpt-5.4", 200_000L, 1_000L, 4L);
        when(executionGateway.execute(any())).thenAnswer(invocation -> {
            var session = invocation.getArgument(0, CopilotPreparedSession.class);
            session.evidenceSink().accept(fetchedCodeEvidence(FETCHED_VALIDATOR_PATH));
            return new CopilotExecutionResult(
                    completeResponse(FETCHED_VALIDATOR_PATH),
                    usage,
                    "crm-copilot-session"
            );
        });
        var externalEvidence = new AtomicReference<pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection>();
        var provider = provider(objectMapper, toolFactory, runPreparation, executionGateway);
        var preparation = promptPreparation(objectMapper);

        var analysis = provider.analyze(
                "crm-provider-run",
                request(),
                context(),
                preparation,
                AnalysisAiAuthRef.localToken(null),
                externalEvidence::set,
                AnalysisAiActivityListener.NO_OP
        );

        assertThat(analysis.status()).isEqualTo(UiExplorerAiAnalysisStatus.COMPLETED);
        assertThat(analysis.result().usage()).isEqualTo(usage);
        assertThat(analysis.sessionId()).isEqualTo("crm-copilot-session");
        assertThat(analysis.preparedPrompt()).contains("UI Explorer canonical prompt");
        assertThat(analysis.report()).isNotNull();
        assertThat(externalEvidence.get()).isEqualTo(fetchedCodeEvidence(FETCHED_VALIDATOR_PATH));
        assertThat(analysis.result().sections())
                .flatExtracting(section -> section.sourceReferences())
                .extracting(reference -> reference.path())
                .containsOnly(FETCHED_VALIDATOR_PATH);
    }

    @Test
    void shouldReturnBlockedFallbackWithoutStartingCopilotWhenReadinessFails() {
        var objectMapper = new ObjectMapper();
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var runPreparation = mock(CopilotRunPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var provider = provider(objectMapper, toolFactory, runPreparation, executionGateway);

        var analysis = provider.analyze(
                "crm-blocked-run",
                request(),
                null,
                null,
                AnalysisAiAuthRef.localToken(null),
                AnalysisAiToolEvidenceListener.NO_OP,
                AnalysisAiActivityListener.NO_OP
        );

        assertThat(analysis.status()).isEqualTo(UiExplorerAiAnalysisStatus.BLOCKED);
        assertThat(analysis.result().usage()).isNull();
        assertThat(analysis.preparedPrompt()).isNull();
        verifyNoInteractions(toolFactory, runPreparation, executionGateway);
    }

    @Test
    void shouldRejectSyntheticCrmResultThatReportsMissingInScopeSnapshotWithoutFallbackAttempt() throws Exception {
        var objectMapper = new ObjectMapper();
        var toolFactory = mock(CopilotSdkToolFactory.class);
        when(toolFactory.createToolDefinitions(any(), any())).thenReturn(registeredTools());
        var runPreparation = mock(CopilotRunPreparationService.class);
        when(runPreparation.prepare(any())).thenReturn(new CopilotPreparedSession(
                "crm-provider-gap-run", null, null, null, "prepared CRM prompt", Map.of()
        ));
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var responseNode = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                completeResponse(pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.EMBEDDED_COMPONENT_PATH)
        );
        responseNode.putArray("visibilityLimits").add("Brak snapshotu komponentu potomnego CRM.");
        var response = objectMapper.writeValueAsString(responseNode);
        when(executionGateway.execute(any())).thenReturn(new CopilotExecutionResult(
                response, null, "crm-copilot-gap-session"
        ));
        var provider = provider(objectMapper, toolFactory, runPreparation, executionGateway);

        var analysis = provider.analyze(
                "crm-provider-gap-run",
                request(),
                context(),
                promptPreparation(objectMapper),
                AnalysisAiAuthRef.localToken(null),
                AnalysisAiToolEvidenceListener.NO_OP,
                AnalysisAiActivityListener.NO_OP
        );

        assertThat(analysis.status()).isEqualTo(UiExplorerAiAnalysisStatus.MALFORMED);
        assertThat(analysis.limitations())
                .contains("AI reported a missing in-scope UI source without attempting the required scoped GitLab fallback.");
    }

    private UiExplorerCopilotAnalysisProvider provider(
            ObjectMapper objectMapper,
            CopilotSdkToolFactory toolFactory,
            CopilotRunPreparationService runPreparation,
            CopilotSdkExecutionGateway executionGateway
    ) {
        var assembler = new UiExplorerCopilotRunRequestAssembler(
                toolFactory,
                new UiExplorerCopilotToolSessionContextFactory(),
                new CopilotRunAuthMapper()
        );
        return new UiExplorerCopilotAnalysisProvider(
                new UiExplorerAiReadinessGate(),
                assembler,
                runPreparation,
                executionGateway,
                new UiExplorerAiResponseParser(objectMapper),
                new DefaultUiExplorerResultReportAssembler()
        );
    }

    private UiExplorerPromptPreparation promptPreparation(ObjectMapper objectMapper) {
        return new UiExplorerPromptPreparationService(
                new UiExplorerArtifactService(objectMapper),
                new CopilotArtifactContentMapper()
        ).prepare(request(), context());
    }

    private List<ToolDefinition> registeredTools() {
        return List.of(
                tool(GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES),
                tool(GitLabToolNames.READ_REPOSITORY_FILE),
                tool(GitLabToolNames.READ_REPOSITORY_FILE_CHUNK)
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
