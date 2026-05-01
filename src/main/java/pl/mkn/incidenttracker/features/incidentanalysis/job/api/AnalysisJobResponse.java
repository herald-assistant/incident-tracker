package pl.mkn.incidenttracker.features.incidentanalysis.job.api;

import pl.mkn.incidenttracker.features.incidentanalysis.flow.AnalysisResultResponse;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record AnalysisJobResponse(
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
        List<AnalysisChatMessageResponse> chatMessages,
        String preparedPrompt,
        AnalysisResultResponse result
) {
}
