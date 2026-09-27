package pl.mkn.tdw.integrations.gitlab.contract.frontend;

public class GitLabFrontendDiscoveryException extends RuntimeException {

    private final String code;

    public GitLabFrontendDiscoveryException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

