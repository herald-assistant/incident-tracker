package pl.mkn.incidenttracker.features.incidentanalysis.job.api;

import pl.mkn.incidenttracker.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

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
        List<AnalysisAiActivityEvent> aiActivityEvents,
        String prompt
) {
}
