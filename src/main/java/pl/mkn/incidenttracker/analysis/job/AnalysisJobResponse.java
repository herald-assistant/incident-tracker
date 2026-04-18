package pl.mkn.incidenttracker.analysis.job;

import pl.mkn.incidenttracker.analysis.AnalysisResultResponse;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record AnalysisJobResponse(
        String analysisId,
        String correlationId,
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
        String preparedPrompt,
        AnalysisResultResponse result
) {
}
