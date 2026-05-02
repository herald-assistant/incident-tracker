package pl.mkn.incidenttracker.features.incidentanalysis.ai.initial;

import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiAuthRef;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiOptions;

import java.util.List;

public record InitialAnalysisRequest(
        String correlationId,
        String environment,
        String gitLabBranch,
        String gitLabGroup,
        List<AnalysisEvidenceSection> evidenceSections,
        AnalysisAiOptions options,
        AnalysisAiAuthRef authRef
) {

    public InitialAnalysisRequest {
        evidenceSections = evidenceSections != null ? List.copyOf(evidenceSections) : List.of();
        options = options != null ? options : AnalysisAiOptions.DEFAULT;
        authRef = authRef != null ? authRef : AnalysisAiAuthRef.localToken(null);
    }

    public InitialAnalysisRequest(
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup,
            List<AnalysisEvidenceSection> evidenceSections,
            AnalysisAiOptions options
    ) {
        this(
                correlationId,
                environment,
                gitLabBranch,
                gitLabGroup,
                evidenceSections,
                options,
                AnalysisAiAuthRef.localToken(null)
        );
    }

    public InitialAnalysisRequest(
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup,
            List<AnalysisEvidenceSection> evidenceSections
    ) {
        this(
                correlationId,
                environment,
                gitLabBranch,
                gitLabGroup,
                evidenceSections,
                AnalysisAiOptions.DEFAULT,
                AnalysisAiAuthRef.localToken(null)
        );
    }
}
