package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

public class CopilotLocalTokenMissingException extends RuntimeException {

    public CopilotLocalTokenMissingException() {
        super("Wprowadź GitHub fine-grained PAT w Workspace Settings, aby uruchomić Copilot.");
    }
}
