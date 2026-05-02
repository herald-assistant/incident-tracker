package pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class GitHubAppCopilotAccessTokenResolver implements CopilotAccessTokenResolver {

    private final GitHubAppCopilotTokenProvider tokenProvider;

    @Override
    public CopilotAccessToken resolve(CopilotRunAuth auth) {
        if (auth == null || !StringUtils.hasText(auth.principalId())) {
            throw new GitHubCopilotAuthRequiredException();
        }

        return tokenProvider.resolve(auth.principalId());
    }
}
