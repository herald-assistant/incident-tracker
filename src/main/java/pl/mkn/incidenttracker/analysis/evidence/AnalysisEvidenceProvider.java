package pl.mkn.incidenttracker.analysis.evidence;

import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.util.List;

public interface AnalysisEvidenceProvider {

    AnalysisEvidenceSection collect(AnalysisContext context);

    AnalysisEvidenceReference producedEvidence();

    default String stepCode() {
        return getClass().getSimpleName();
    }

    default String stepLabel() {
        return stepCode();
    }

    default AnalysisStepPhase stepPhase() {
        return AnalysisStepPhase.ENRICHMENT;
    }

    default List<AnalysisEvidenceReference> consumedEvidence() {
        return List.of();
    }

    default AnalysisEvidenceProviderDescriptor descriptor() {
        return new AnalysisEvidenceProviderDescriptor(
                stepCode(),
                stepLabel(),
                stepPhase(),
                List.copyOf(consumedEvidence()),
                List.of(producedEvidence())
        );
    }

}
