package pl.mkn.incidenttracker.integrations.github.auth;

public class GitHubOAuthStateInvalidException extends RuntimeException {

    public GitHubOAuthStateInvalidException(String message) {
        super(message);
    }
}
