package pl.mkn.tdw.features.uiexplorer.job.api;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record UiExplorerJobStateSnapshot(
        String jobId,
        UiExplorerJobRequestSnapshot request,
        UiExplorerJobStatus status,
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
        UiExplorerResultResponse result,
        AnalysisReport report,
        AnalysisAiUsage usage,
        UiExplorerSourceRevision sourceRevision,
        UiExplorerOutputAvailability outputAvailability,
        boolean exportAvailable,
        List<AnalysisChatMessageResponse> chatMessages,
        UiExplorerChatAvailability chatAvailability
) {

    public UiExplorerJobStateSnapshot {
        steps = steps != null ? List.copyOf(steps) : List.of();
        contextSections = contextSections != null ? List.copyOf(contextSections) : List.of();
        toolEvidenceSections = toolEvidenceSections != null ? List.copyOf(toolEvidenceSections) : List.of();
        aiActivityEvents = aiActivityEvents != null ? List.copyOf(aiActivityEvents) : List.of();
        toolFeedback = toolFeedback != null ? List.copyOf(toolFeedback) : List.of();
        chatMessages = chatMessages != null ? List.copyOf(chatMessages) : List.of();
        chatAvailability = chatAvailability != null
                ? chatAvailability
                : new UiExplorerChatAvailability(false, "UI_EXPLORER_CHAT_UNAVAILABLE", "Follow-up chat is unavailable.");
    }

    public UiExplorerJobStateSnapshot(
            String jobId, UiExplorerJobRequestSnapshot request, UiExplorerJobStatus status,
            String currentStepCode, String currentStepLabel, String errorCode, String errorMessage,
            Instant createdAt, Instant updatedAt, Instant completedAt,
            List<AnalysisJobStepResponse> steps, List<AnalysisEvidenceSection> contextSections,
            List<AnalysisEvidenceSection> toolEvidenceSections, List<AnalysisAiActivityEvent> aiActivityEvents,
            List<AnalysisAiToolFeedback> toolFeedback, String preparedPrompt, UiExplorerResultResponse result,
            AnalysisReport report, AnalysisAiUsage usage, UiExplorerSourceRevision sourceRevision,
            UiExplorerOutputAvailability outputAvailability, boolean exportAvailable
    ) {
        this(jobId, request, status, currentStepCode, currentStepLabel, errorCode, errorMessage,
                createdAt, updatedAt, completedAt, steps, contextSections, toolEvidenceSections,
                aiActivityEvents, toolFeedback, preparedPrompt, result, report, usage, sourceRevision,
                outputAvailability, exportAvailable, List.of(), null);
    }
}
