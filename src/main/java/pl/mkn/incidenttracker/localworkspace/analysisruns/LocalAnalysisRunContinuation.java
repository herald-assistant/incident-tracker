package pl.mkn.incidenttracker.localworkspace.analysisruns;

public record LocalAnalysisRunContinuation(
        boolean enabled,
        String gitLabGroup,
        String authMode,
        String authPrincipalRef,
        String copilotSessionId,
        String copilotRuntime,
        String continuationMode
) {
    public static final String COPILOT_RUNTIME_GITHUB_COPILOT_SDK = "github-copilot-sdk";
    public static final String CONTINUATION_MODE_COPILOT_SESSION = "copilot-session";

    public LocalAnalysisRunContinuation(
            boolean enabled,
            String gitLabGroup,
            String authMode,
            String authPrincipalRef
    ) {
        this(enabled, gitLabGroup, authMode, authPrincipalRef, null, null, null);
    }

    public LocalAnalysisRunContinuation withLatestCopilotSession(String latestCopilotSessionId) {
        if (!hasText(latestCopilotSessionId)) {
            return this;
        }

        return new LocalAnalysisRunContinuation(
                enabled,
                gitLabGroup,
                authMode,
                authPrincipalRef,
                latestCopilotSessionId.trim(),
                hasText(copilotRuntime) ? copilotRuntime : COPILOT_RUNTIME_GITHUB_COPILOT_SDK,
                CONTINUATION_MODE_COPILOT_SESSION
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
