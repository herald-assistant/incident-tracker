package pl.mkn.tdw.features.incidentanalysis.job.api;

import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisResultResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiToolFeedback;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record AnalysisJobStateSnapshot(
        String analysisId,
        String correlationId,
        String aiModel,
        String reasoningEffort,
        String status,
        String currentStepCode,
        String currentStepLabel,
        String environment,
        String gitLabBranch,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<AnalysisJobStepResponse> steps,
        List<AnalysisEvidenceSection> evidenceSections,
        List<AnalysisEvidenceSection> toolEvidenceSections,
        List<AnalysisAiActivityEvent> aiActivityEvents,
        List<AnalysisAiToolFeedback> toolFeedback,
        List<AnalysisChatMessageResponse> chatMessages,
        String preparedPrompt,
        AnalysisResultResponse result
) {
    public AnalysisJobStateSnapshot(
            String analysisId,
            String correlationId,
            String aiModel,
            String reasoningEffort,
            String status,
            String currentStepCode,
            String currentStepLabel,
            String environment,
            String gitLabBranch,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            List<AnalysisJobStepResponse> steps,
            List<AnalysisEvidenceSection> evidenceSections,
            List<AnalysisEvidenceSection> toolEvidenceSections,
            List<AnalysisAiActivityEvent> aiActivityEvents,
            List<AnalysisChatMessageResponse> chatMessages,
            String preparedPrompt,
            AnalysisResultResponse result
    ) {
        this(
                analysisId,
                correlationId,
                aiModel,
                reasoningEffort,
                status,
                currentStepCode,
                currentStepLabel,
                environment,
                gitLabBranch,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt,
                completedAt,
                steps,
                evidenceSections,
                toolEvidenceSections,
                aiActivityEvents,
                List.of(),
                chatMessages,
                preparedPrompt,
                result
        );
    }
}
