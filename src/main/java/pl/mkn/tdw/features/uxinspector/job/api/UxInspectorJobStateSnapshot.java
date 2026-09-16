package pl.mkn.tdw.features.uxinspector.job.api;

import pl.mkn.tdw.features.uxinspector.context.UxInspectorSourceRevision;
import pl.mkn.tdw.features.uxinspector.contract.UxInspectorResultResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record UxInspectorJobStateSnapshot(
        String jobId,
        UxInspectorJobRequestSnapshot request,
        UxInspectorJobStatus status,
        String currentStepCode,
        String currentStepLabel,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<AnalysisJobStepResponse> steps,
        List<AnalysisEvidenceSection> contextSections,
        List<AnalysisEvidenceSection> toolEvidenceSections,
        List<AnalysisAiActivityEvent> aiActivityEvents,
        List<AnalysisAiToolFeedback> toolFeedback,
        String preparedPrompt,
        UxInspectorResultResponse result,
        AnalysisReport report,
        AnalysisAiUsage usage,
        UxInspectorSourceRevision sourceRevision,
        UxInspectorOutputAvailability outputAvailability,
        boolean exportAvailable
) {
    public UxInspectorJobStateSnapshot {
        steps = steps != null ? List.copyOf(steps) : List.of();
        contextSections = contextSections != null ? List.copyOf(contextSections) : List.of();
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        aiActivityEvents = aiActivityEvents != null ? List.copyOf(aiActivityEvents) : List.of();
        toolFeedback = toolFeedback != null ? List.copyOf(toolFeedback) : List.of();
    }
}

