package pl.mkn.incidenttracker.analysis.ai;

import pl.mkn.incidenttracker.analysis.AnalysisMode;

import java.util.List;

public record AnalysisAiAnalysisRequest(
        String correlationId,
        String environment,
        String gitLabBranch,
        String gitLabGroup,
        AnalysisMode mode,
        List<AnalysisEvidenceSection> evidenceSections,
        AnalysisAiPriorResult priorResult
) {

    public AnalysisAiAnalysisRequest(
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup,
            AnalysisMode mode,
            List<AnalysisEvidenceSection> evidenceSections
    ) {
        this(correlationId, environment, gitLabBranch, gitLabGroup, mode, evidenceSections, null);
    }
}
