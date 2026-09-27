package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

public record CopilotRunAuth() {

    public static CopilotRunAuth localToken() {
        return new CopilotRunAuth();
    }
}
