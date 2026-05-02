package pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth;

public interface CopilotAccessTokenResolver {

    CopilotAccessToken resolve(CopilotRunAuth auth);
}
