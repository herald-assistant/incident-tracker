package pl.mkn.incidenttracker.integrations.gitlab;

public class GitLabRepositoryTreeException extends RuntimeException {

    private final int statusCode;

    public GitLabRepositoryTreeException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
