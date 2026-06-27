package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

public interface GitHubAppCopilotTokenProvider {

    CopilotAccessToken resolve(String principalId);
}
