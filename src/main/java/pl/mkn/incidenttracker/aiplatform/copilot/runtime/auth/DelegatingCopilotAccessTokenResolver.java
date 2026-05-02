package pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class DelegatingCopilotAccessTokenResolver implements CopilotAccessTokenResolver {

    private final LocalCopilotAccessTokenResolver localResolver;
    private final GitHubAppCopilotAccessTokenResolver githubAppResolver;

    @Override
    public CopilotAccessToken resolve(CopilotRunAuth auth) {
        var mode = auth != null && auth.mode() != null ? auth.mode() : CopilotAuthMode.LOCAL_TOKEN;
        return switch (mode) {
            case LOCAL_TOKEN -> localResolver.resolve(auth);
            case GITHUB_APP -> githubAppResolver.resolve(auth);
        };
    }
}
