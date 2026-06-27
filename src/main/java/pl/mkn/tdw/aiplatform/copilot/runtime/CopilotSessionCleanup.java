package pl.mkn.tdw.aiplatform.copilot.runtime;

import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

public interface CopilotSessionCleanup {

    CopilotSessionCleanup NO_OP = (sessionId, auth) -> {
    };

    void deleteSession(String sessionId, CopilotRunAuth auth);
}
