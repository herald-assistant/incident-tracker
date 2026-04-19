package pl.mkn.incidenttracker.analysis.job;

import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;

import java.time.Instant;
import java.util.List;

public record AnalysisJobStepResponse(
        String code,
        String label,
        String phase,
        String status,
        String message,
        Integer itemCount,
        Instant startedAt,
        Instant completedAt,
        String variantMode,
        String preparedPrompt,
        List<AnalysisEvidenceReference> consumesEvidence,
        List<AnalysisEvidenceReference> producesEvidence
) {
}
