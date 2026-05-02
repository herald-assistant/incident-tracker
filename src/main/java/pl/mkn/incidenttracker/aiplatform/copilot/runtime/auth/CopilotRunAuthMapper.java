package pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth;

import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiAuthRef;

@Component
public class CopilotRunAuthMapper {

    public CopilotRunAuth toRunAuth(AnalysisAiAuthRef authRef) {
        if (authRef == null) {
            return CopilotRunAuth.localToken();
        }

        return new CopilotRunAuth(
                CopilotAuthMode.from(authRef.mode()),
                authRef.principalId(),
                AnalysisAiAuthRef.MODE_GITHUB_APP.equals(authRef.mode())
                        ? authRef.providerAccountLabel()
                        : null,
                authRef.userBilling()
        );
    }
}
