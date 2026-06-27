package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

public class GitHubCopilotAuthRequiredException extends RuntimeException {

    private static final String DEFAULT_AUTH_START_URL = "/api/auth/github/start";

    private final String authStartUrl;

    public GitHubCopilotAuthRequiredException() {
        this("Połącz konto GitHub, aby uruchomić analizę przez Copilot.");
    }

    public GitHubCopilotAuthRequiredException(String message) {
        super(message);
        this.authStartUrl = DEFAULT_AUTH_START_URL;
    }

    public String authStartUrl() {
        return authStartUrl;
    }
}
