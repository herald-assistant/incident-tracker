package pl.mkn.tdw.features.uxinspector.ai.copilot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.uxinspector.ai.*;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus;
import pl.mkn.tdw.features.uxinspector.job.api.UxInspectorJobStartRequest;
import pl.mkn.tdw.features.uxinspector.report.UxInspectorReportMapper;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UxInspectorCopilotAnalysisProvider implements UxInspectorAnalysisProvider {
    private final UxInspectorCopilotRunRequestAssembler assembler;
    private final CopilotRunPreparationService preparationService;
    private final CopilotSdkExecutionGateway executionGateway;
    private final UxInspectorReportMapper reportMapper;

    @Override
    public UxInspectorAiAnalysis analyze(String runReference, UxInspectorJobStartRequest request,
                                         UxInspectorTargetContext context, UxInspectorPromptPreparation preparation,
                                         AnalysisAiAuthRef authRef, AnalysisAiToolEvidenceListener evidenceListener,
                                         AnalysisAiActivityListener activityListener) {
        if (context.status() == UxInspectorTargetResolutionStatus.NOT_FOUND) {
            return new UxInspectorAiAnalysis(UxInspectorAiAnalysisStatus.BLOCKED, null, null, null, null,
                    List.of("No verified source target exists in the selected view and pinned revision."));
        }
        var assembly = assembler.assemble(runReference, request, context, preparation, authRef);
        if (!assembly.toolAccessPolicy().reportToolsAvailable()
                || !assembly.toolAccessPolicy().targetToolsAvailable()
                || !assembly.toolAccessPolicy().sourceToolsAvailable()) {
            return new UxInspectorAiAnalysis(UxInspectorAiAnalysisStatus.BLOCKED, null, null, null, null,
                    List.of("Required UX Inspector report, target or source tools are unavailable."));
        }
        var prepared = preparationService.prepare(assembly.runRequest()).withEvidenceSink(section -> {
            if (evidenceListener != null) evidenceListener.onToolEvidenceUpdated(section);
        });
        if (activityListener != null) prepared = prepared.withActivitySink(activityListener::onAiActivity);
        var execution = executionGateway.execute(prepared);
        var mapping = reportMapper.map(execution.report(), request.capture(), context,
                assembly.repositoryToolScope().readSourceRefs(), execution.usage());
        if (mapping.result() == null || mapping.report() == null) {
            return new UxInspectorAiAnalysis(UxInspectorAiAnalysisStatus.FAILED, null, null, execution.usage(),
                    execution.sessionId(), mapping.limitations());
        }
        return new UxInspectorAiAnalysis(mapping.complete() ? UxInspectorAiAnalysisStatus.COMPLETED
                : UxInspectorAiAnalysisStatus.PARTIAL, mapping.result(), mapping.report(), execution.usage(),
                execution.sessionId(), mapping.limitations());
    }
}
