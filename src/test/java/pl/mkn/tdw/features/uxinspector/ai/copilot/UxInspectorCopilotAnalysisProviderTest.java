package pl.mkn.tdw.features.uxinspector.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorAiAnalysisStatus;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorPromptPreparation;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus;
import pl.mkn.tdw.features.uxinspector.contract.UxInspectorResultResponse;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStartRequest;
import pl.mkn.tdw.features.uxinspector.report.UxInspectorReportMapper;
import pl.mkn.tdw.features.uxinspector.report.UxInspectorReportMapping;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorCopilotAnalysisProviderTest {

    @Test
    void shouldContinueWithRepositoryResearchWhenTheTargetWasNotFound() {
        var assembler = mock(UxInspectorCopilotRunRequestAssembler.class);
        var preparationService = mock(CopilotRunPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var reportMapper = mock(UxInspectorReportMapper.class);
        var policy = mock(UxInspectorCopilotToolAccessPolicy.class);
        when(policy.reportToolsAvailable()).thenReturn(true);
        when(policy.targetToolsAvailable()).thenReturn(true);
        when(policy.sourceToolsAvailable()).thenReturn(true);
        var runRequest = mock(CopilotRunRequest.class);
        var scope = new GitLabRepositoryToolScope("CRM", "crm-ui", "main", REVISION);
        when(assembler.assemble(anyString(), any(), any(), any(), any())).thenReturn(
                new UxInspectorCopilotRunAssembly(runRequest, null, scope, policy, null));
        var preparedSession = mock(CopilotPreparedSession.class);
        when(preparationService.prepare(runRequest)).thenReturn(preparedSession);
        when(preparedSession.withEvidenceSink(any())).thenReturn(preparedSession);
        var report = mock(AnalysisReport.class);
        when(executionGateway.execute(preparedSession)).thenReturn(
                new CopilotExecutionResult("", null, "crm-session", report));
        var result = mock(UxInspectorResultResponse.class);
        when(reportMapper.map(eq(report), any(), any(), anySet(), anySet(), isNull())).thenReturn(
                new UxInspectorReportMapping(result, report, false, List.of("Target was not resolved.")));
        var provider = new UxInspectorCopilotAnalysisProvider(
                assembler, preparationService, executionGateway, reportMapper);
        var context = notFoundContext();
        var request = new UxInspectorJobStartRequest(
                "crm-agent-portal", "main", VIEW_ID, REVISION, "Wyjasnij ten element.",
                capture(), "gpt-crm", "medium");
        var preparation = new UxInspectorPromptPreparation(
                "Prompt with component source pack", Map.of("component-pack", "all components"),
                Set.of(SOURCE_PATH, TEMPLATE_PATH));

        var analysis = provider.analyze(
                "crm-run", request, context, preparation, AnalysisAiAuthRef.localToken("CRM test"), null, null);

        assertThat(analysis.status()).isEqualTo(UxInspectorAiAnalysisStatus.PARTIAL);
        verify(assembler).assemble("crm-run", request, context, preparation, AnalysisAiAuthRef.localToken("CRM test"));
        verify(executionGateway).execute(preparedSession);
    }

    private UxInspectorTargetContext notFoundContext() {
        var original = targetContext();
        return new UxInspectorTargetContext(
                original.systemId(), original.systemLabel(), original.sourceScope(), original.view(),
                original.sourceRevision(), UxInspectorTargetResolutionStatus.NOT_FOUND, List.of(), null, "",
                List.of("No source target could be verified in the selected view and pinned revision."),
                original.graph());
    }
}
