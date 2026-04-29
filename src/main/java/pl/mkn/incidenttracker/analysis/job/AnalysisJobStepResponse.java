package pl.mkn.incidenttracker.analysis.job;

import pl.mkn.incidenttracker.analysis.ai.AnalysisAiUsage;
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
        List<AnalysisEvidenceReference> consumesEvidence,
        List<AnalysisEvidenceReference> producesEvidence,
        AnalysisAiUsage usage
) {

    public AnalysisJobStepResponse(
            String code,
            String label,
            String phase,
            String status,
            String message,
            Integer itemCount,
            Instant startedAt,
            Instant completedAt,
            List<AnalysisEvidenceReference> consumesEvidence,
            List<AnalysisEvidenceReference> producesEvidence
    ) {
        this(
                code,
                label,
                phase,
                status,
                message,
                itemCount,
                startedAt,
                completedAt,
                consumesEvidence,
                producesEvidence,
                null
        );
    }
}
