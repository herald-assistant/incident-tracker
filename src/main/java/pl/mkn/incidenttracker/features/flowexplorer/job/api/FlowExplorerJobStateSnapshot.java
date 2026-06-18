package pl.mkn.incidenttracker.features.flowexplorer.job.api;

import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record FlowExplorerJobStateSnapshot(
        String jobId,
        String systemId,
        String endpointId,
        String httpMethod,
        String endpointPath,
        String branch,
        FlowExplorerDocumentationPreset documentationPreset,
        List<FlowExplorerFocusArea> focusAreas,
        String aiModel,
        String reasoningEffort,
        String status,
        String currentStepCode,
        String currentStepLabel,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<FlowExplorerJobStepResponse> steps,
        FlowExplorerContextSnapshot contextSnapshot,
        List<AnalysisEvidenceSection> contextSections,
        List<AnalysisEvidenceSection> toolEvidenceSections,
        List<AnalysisAiActivityEvent> aiActivityEvents,
        List<AnalysisAiToolFeedback> toolFeedback,
        List<FlowExplorerChatMessageResponse> chatMessages,
        String preparedPrompt,
        FlowExplorerResultResponse result
) {

    public FlowExplorerJobStateSnapshot {
        focusAreas = focusAreas != null ? List.copyOf(focusAreas) : List.of();
        steps = steps != null ? List.copyOf(steps) : List.of();
        contextSections = contextSections != null ? List.copyOf(contextSections) : List.of();
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        aiActivityEvents = aiActivityEvents != null ? List.copyOf(aiActivityEvents) : List.of();
        toolFeedback = toolFeedback != null ? List.copyOf(toolFeedback) : List.of();
        chatMessages = chatMessages != null ? List.copyOf(chatMessages) : List.of();
    }
}
