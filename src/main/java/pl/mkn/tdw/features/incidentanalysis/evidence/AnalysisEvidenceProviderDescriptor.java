package pl.mkn.tdw.features.incidentanalysis.evidence;

import pl.mkn.tdw.shared.evidence.AnalysisEvidenceReference;

public record AnalysisEvidenceProviderDescriptor(
        String stepCode,
        String stepLabel,
        AnalysisStepPhase phase,
        java.util.List<AnalysisEvidenceReference> consumesEvidence,
        java.util.List<AnalysisEvidenceReference> producesEvidence
) {
}
