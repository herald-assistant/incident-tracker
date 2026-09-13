package pl.mkn.tdw.features.incidentanalysis.ai.initial;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.List;

public record InitialAnalysisRequest(
        String correlationId,
        String environment,
        String gitLabBranch,
        String gitLabGroup,
        List<AnalysisEvidenceSection> evidenceSections,
        AnalysisAiOptions options,
        AnalysisAiAuthRef authRef,
        String problemDescription
) {

    public InitialAnalysisRequest {
        evidenceSections = evidenceSections != null ? List.copyOf(evidenceSections) : List.of();
        options = options != null ? options : AnalysisAiOptions.DEFAULT;
        authRef = authRef != null ? authRef : AnalysisAiAuthRef.localToken(null);
        problemDescription = StringUtils.hasText(problemDescription) ? problemDescription.trim() : null;
    }

    public InitialAnalysisRequest(
            String correlationId,
            String environment,
            String gitLabBranch,
            String gitLabGroup,
            List<AnalysisEvidenceSection> evidenceSections,
            AnalysisAiOptions options,
            AnalysisAiAuthRef authRef
    ) {
        this(correlationId, environment, gitLabBranch, gitLabGroup, evidenceSections, options, authRef, null);
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
