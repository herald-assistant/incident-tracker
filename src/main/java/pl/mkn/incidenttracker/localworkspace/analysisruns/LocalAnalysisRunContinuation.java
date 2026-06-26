package pl.mkn.incidenttracker.localworkspace.analysisruns;

public record LocalAnalysisRunContinuation(
        boolean enabled,
        String gitLabGroup,
        String authMode,
        String authPrincipalRef
) {
}
