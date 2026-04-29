package pl.mkn.incidenttracker.analysis.ai.initial;

import pl.mkn.incidenttracker.analysis.ai.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.options.AnalysisAiOptions;

import java.util.List;

public record InitialAnalysisRequest(
        String correlationId,
        String environment,
        String gitLabBranch,
        String gitLabGroup,
        List<AnalysisEvidenceSection> evidenceSections,
        AnalysisAiOptions options
) {

    public InitialAnalysisRequest {
        evidenceSections = evidenceSections != null ? List.copyOf(evidenceSections) : List.of();
        options = options != null ? options : AnalysisAiOptions.DEFAULT;
    }

    public InitialAnalysisRequest(
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup,
            List<AnalysisEvidenceSection> evidenceSections
    ) {
        this(correlationId, environment, gitLabBranch, gitLabGroup, evidenceSections, AnalysisAiOptions.DEFAULT);
    }
}
