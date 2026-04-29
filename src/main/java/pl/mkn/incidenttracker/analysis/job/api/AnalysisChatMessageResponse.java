package pl.mkn.incidenttracker.analysis.job.api;

import pl.mkn.incidenttracker.analysis.ai.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;

public record AnalysisChatMessageResponse(
        String id,
        String role,
        String status,
        String content,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<AnalysisEvidenceSection> toolEvidenceSections,
        String prompt
) {
}
