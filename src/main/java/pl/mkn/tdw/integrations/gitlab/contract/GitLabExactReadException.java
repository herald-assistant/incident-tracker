package pl.mkn.tdw.integrations.gitlab.contract;

public class GitLabExactReadException extends RuntimeException {

    private final GitLabExactReadError error;
    private final Integer upstreamStatus;

    private GitLabExactReadException(
            GitLabExactReadError error,
            Integer upstreamStatus,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.error = error;
        this.upstreamStatus = upstreamStatus;
    }

    public GitLabExactReadError error() {
        return error;
    }

    public Integer upstreamStatus() {
        return upstreamStatus;
    }

    public static GitLabExactReadException invalidTarget(String message) {
        return new GitLabExactReadException(GitLabExactReadError.INVALID_TARGET, null, message, null);
    }

    public static GitLabExactReadException connectionNotFound(String connectionId) {
        return new GitLabExactReadException(
                GitLabExactReadError.CONNECTION_NOT_FOUND,
                null,
                "Named GitLab connection is not configured: " + connectionId,
                null
        );
    }

    public static GitLabExactReadException connectionInvalid(String connectionId) {
        return new GitLabExactReadException(
                GitLabExactReadError.CONNECTION_INVALID,
                null,
                "Named GitLab connection has invalid configuration: " + connectionId,
                null
        );
    }

    public static GitLabExactReadException upstream(
            String operation,
            String connectionId,
            int status,
            Throwable cause
    ) {
        var error = switch (status) {
            case 401 -> GitLabExactReadError.UNAUTHORIZED;
            case 403 -> GitLabExactReadError.FORBIDDEN;
            case 404 -> GitLabExactReadError.NOT_FOUND;
            default -> GitLabExactReadError.UPSTREAM_FAILURE;
        };
        return new GitLabExactReadException(
                error,
                status,
                "GitLab " + operation + " failed through named connection " + connectionId + ".",
                cause
        );
    }

    public static GitLabExactReadException transportFailure(
            String operation,
            String connectionId,
            Throwable cause
    ) {
        return new GitLabExactReadException(
                GitLabExactReadError.UPSTREAM_FAILURE,
                null,
                "GitLab " + operation + " failed through named connection " + connectionId + ".",
                cause
        );
    }
}
