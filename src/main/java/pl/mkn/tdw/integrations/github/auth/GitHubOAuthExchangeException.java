package pl.mkn.tdw.integrations.github.auth;

public class GitHubOAuthExchangeException extends RuntimeException {

    public GitHubOAuthExchangeException(String message) {
        super(message);
    }

    public GitHubOAuthExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
