package pl.mkn.tdw.api.githubauth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubAppCopilotTokenProvider;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotAuthRequiredException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotReauthRequiredException;
import pl.mkn.tdw.integrations.github.auth.GitHubAppAuthorizationReauthRequiredException;
import pl.mkn.tdw.integrations.github.auth.GitHubAppAuthorizationStore;
import pl.mkn.tdw.integrations.github.auth.GitHubAppTokenRefreshService;
import pl.mkn.tdw.integrations.github.auth.GitHubTokenCipher;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class GitHubAppCopilotTokenProviderAdapter implements GitHubAppCopilotTokenProvider {

    private final GitHubAppAuthorizationStore authorizationStore;
    private final GitHubAppTokenRefreshService tokenRefreshService;
    private final GitHubTokenCipher tokenCipher;

    @Override
    public CopilotAccessToken resolve(String principalId) {
        var authorization = authorizationStore.findActiveByOperatorSessionId(principalId)
                .orElseThrow(GitHubCopilotAuthRequiredException::new);

        try {
            var freshAuthorization = tokenRefreshService.ensureFresh(authorization, Instant.now());
            return new CopilotAccessToken(
                    tokenCipher.decrypt(freshAuthorization.encryptedAccessToken()),
                    freshAuthorization.githubLogin(),
                    freshAuthorization.accessTokenExpiresAt(),
                    true
            );
        } catch (GitHubAppAuthorizationReauthRequiredException exception) {
            throw new GitHubCopilotReauthRequiredException();
        }
    }
}
