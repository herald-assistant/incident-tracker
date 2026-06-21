package pl.mkn.incidenttracker.shared.ai;

import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceReference;

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

    public AnalysisJobStepResponse {
        consumesEvidence = consumesEvidence != null ? List.copyOf(consumesEvidence) : List.of();
        producesEvidence = producesEvidence != null ? List.copyOf(producesEvidence) : List.of();
    }

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
