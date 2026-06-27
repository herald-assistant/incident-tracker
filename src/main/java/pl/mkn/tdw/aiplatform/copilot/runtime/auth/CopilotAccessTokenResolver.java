package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

public interface CopilotAccessTokenResolver {

    CopilotAccessToken resolve(CopilotRunAuth auth);
}
