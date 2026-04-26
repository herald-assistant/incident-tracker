package pl.mkn.incidenttracker.analysis.ai;

import java.util.List;

public record AnalysisAiAnalysisRequest(
        String correlationId,
        String environment,
        String gitLabBranch,
        String gitLabGroup,
        List<AnalysisEvidenceSection> evidenceSections
) {

    public AnalysisAiAnalysisRequest {
        evidenceSections = evidenceSections != null ? List.copyOf(evidenceSections) : List.of();
    }
}
