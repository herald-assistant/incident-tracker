package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

public class GitHubCopilotReauthRequiredException extends RuntimeException {

    private static final String DEFAULT_AUTH_START_URL = "/api/auth/github/start";

    private final String authStartUrl;

    public GitHubCopilotReauthRequiredException() {
        this("Połącz ponownie GitHub, aby kontynuować pracę z Copilot.");
    }

    public GitHubCopilotReauthRequiredException(String message) {
        super(message);
        this.authStartUrl = DEFAULT_AUTH_START_URL;
    }

    public String authStartUrl() {
        return authStartUrl;
    }
}
