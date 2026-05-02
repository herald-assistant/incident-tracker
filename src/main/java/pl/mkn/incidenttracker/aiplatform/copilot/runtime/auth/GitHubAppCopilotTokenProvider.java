package pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth;

public interface GitHubAppCopilotTokenProvider {

    CopilotAccessToken resolve(String principalId);
}
