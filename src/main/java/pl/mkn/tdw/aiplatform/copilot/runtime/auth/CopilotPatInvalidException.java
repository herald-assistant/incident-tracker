package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

public class CopilotPatInvalidException extends RuntimeException {

    public CopilotPatInvalidException() {
        super("Workspace Settings wymaga GitHub fine-grained PAT (github_pat_) z uprawnieniem Copilot Requests.");
    }
}
