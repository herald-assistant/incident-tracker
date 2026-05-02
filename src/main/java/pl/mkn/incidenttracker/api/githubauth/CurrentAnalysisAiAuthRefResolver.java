package pl.mkn.incidenttracker.api.githubauth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotAuthMode;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.GitHubCopilotAuthRequiredException;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubAppAuthorizationStore;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiAuthRef;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiAuthRefResolver;

@Component
@RequiredArgsConstructor
public class CurrentAnalysisAiAuthRefResolver implements AnalysisAiAuthRefResolver {

    private final CopilotSdkProperties copilotProperties;
    private final OperatorSessionService operatorSessionService;
    private final GitHubAppAuthorizationStore authorizationStore;

    @Override
    public AnalysisAiAuthRef resolveForCurrentRequest() {
        if (copilotProperties.getAuth().getMode() == CopilotAuthMode.LOCAL_TOKEN) {
            return AnalysisAiAuthRef.localToken(copilotProperties.getAuth().getLocal().getDisplayName());
        }

        var sessionId = operatorSessionService.currentSessionId()
                .orElseThrow(GitHubCopilotAuthRequiredException::new);
        var authorization = authorizationStore.findActiveByOperatorSessionId(sessionId)
                .orElseThrow(GitHubCopilotAuthRequiredException::new);

        return AnalysisAiAuthRef.githubApp(sessionId, authorization.githubLogin());
    }
}
