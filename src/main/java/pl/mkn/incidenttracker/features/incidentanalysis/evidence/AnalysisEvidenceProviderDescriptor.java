package pl.mkn.incidenttracker.features.incidentanalysis.evidence;

import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceReference;

public record AnalysisEvidenceProviderDescriptor(
        String stepCode,
        String stepLabel,
        AnalysisStepPhase phase,
        java.util.List<AnalysisEvidenceReference> consumesEvidence,
        java.util.List<AnalysisEvidenceReference> producesEvidence
) {
}
