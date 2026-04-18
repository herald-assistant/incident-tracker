package pl.mkn.incidenttracker.analysis.evidence;

public record AnalysisEvidenceProviderDescriptor(
        String stepCode,
        String stepLabel,
        AnalysisStepPhase phase,
        java.util.List<AnalysisEvidenceReference> consumesEvidence,
        java.util.List<AnalysisEvidenceReference> producesEvidence
) {
}
