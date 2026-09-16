package pl.mkn.tdw.features.uxinspector.contract;

import pl.mkn.tdw.features.uxinspector.context.UxInspectorSourceRevision;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetResolutionStatus;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorViewIdentity;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReportReference;

import java.util.List;

public record UxInspectorResultResponse(
        String captureId,
        String targetLabel,
        UxInspectorViewIdentity view,
        UxInspectorSourceRevision sourceRevision,
        UxInspectorTargetResolutionStatus resolutionStatus,
        String thesis,
        String answer,
        String confidence,
        List<AnalysisReportReference> sourceReferences,
        List<String> visibilityLimits,
        List<String> openQuestions,
        AnalysisAiUsage usage
) {
    public UxInspectorResultResponse {
        sourceReferences = sourceReferences != null ? List.copyOf(sourceReferences) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        openQuestions = openQuestions != null ? List.copyOf(openQuestions) : List.of();
    }
}

