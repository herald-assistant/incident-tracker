package pl.mkn.tdw.integrations.github.auth;

public class GitHubAppAuthorizationReauthRequiredException extends RuntimeException {

    public GitHubAppAuthorizationReauthRequiredException(String message) {
        super(message);
    }
}
