package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

public class CopilotLocalTokenMissingException extends RuntimeException {

    public CopilotLocalTokenMissingException() {
        super("Tryb LOCAL_TOKEN wymaga skonfigurowania analysis.ai.copilot.auth.local.github-token albo COPILOT_GITHUB_TOKEN.");
    }
}
