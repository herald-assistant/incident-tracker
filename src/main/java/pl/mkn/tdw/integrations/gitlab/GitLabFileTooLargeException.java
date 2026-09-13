package pl.mkn.tdw.integrations.gitlab;

public class GitLabFileTooLargeException extends IllegalStateException {

    public GitLabFileTooLargeException(String filePath, int maxBytes) {
        super("GitLab file exceeds the bounded read limit of " + maxBytes + " bytes: " + filePath);
    }
}
