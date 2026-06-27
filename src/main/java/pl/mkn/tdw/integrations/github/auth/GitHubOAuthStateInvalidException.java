package pl.mkn.tdw.integrations.github.auth;

public class GitHubOAuthStateInvalidException extends RuntimeException {

    public GitHubOAuthStateInvalidException(String message) {
        super(message);
    }
}
