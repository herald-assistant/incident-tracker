package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@Component
public class CopilotRunAuthMapper {

    public CopilotRunAuth toRunAuth(AnalysisAiAuthRef authRef) {
        return CopilotRunAuth.localToken();
    }
}
