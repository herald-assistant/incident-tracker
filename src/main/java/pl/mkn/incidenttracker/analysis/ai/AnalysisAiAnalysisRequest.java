package pl.mkn.incidenttracker.analysis.ai;

import java.util.List;

public record AnalysisAiAnalysisRequest(
        String correlationId,
        String environment,
        String gitLabBranch,
        String gitLabGroup,
        List<AnalysisEvidenceSection> evidenceSections,
        AnalysisAiOptions options
) {

    public AnalysisAiAnalysisRequest {
        evidenceSections = evidenceSections != null ? List.copyOf(evidenceSections) : List.of();
        options = options != null ? options : AnalysisAiOptions.DEFAULT;
    }

    public AnalysisAiAnalysisRequest(
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup,
            List<AnalysisEvidenceSection> evidenceSections
    ) {
        this(correlationId, environment, gitLabBranch, gitLabGroup, evidenceSections, AnalysisAiOptions.DEFAULT);
    }
}
