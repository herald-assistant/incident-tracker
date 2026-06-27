package pl.mkn.tdw.api.githubauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAuthMode;
import pl.mkn.tdw.integrations.github.auth.GitHubAppAuthProperties;
import pl.mkn.tdw.integrations.github.auth.GitHubAppAuthorization;
import pl.mkn.tdw.integrations.github.auth.GitHubAppAuthorizationStore;
import pl.mkn.tdw.integrations.github.auth.GitHubAppOAuthClient;
import pl.mkn.tdw.integrations.github.auth.GitHubOAuthExchangeException;
import pl.mkn.tdw.integrations.github.auth.GitHubOAuthState;
import pl.mkn.tdw.integrations.github.auth.GitHubOAuthStateInvalidException;
import pl.mkn.tdw.integrations.github.auth.GitHubOAuthStateStore;
import pl.mkn.tdw.integrations.github.auth.GitHubTokenCipher;
import pl.mkn.tdw.integrations.github.auth.GitHubUserProfileClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class GitHubAuthService {

    private static final String AUTH_START_URL = "/api/auth/github/start";

    private final CopilotSdkProperties copilotProperties;
    private final GitHubAppAuthProperties githubProperties;
    private final OperatorSessionService operatorSessionService;
    private final GitHubOAuthStateStore stateStore;
    private final GitHubAppOAuthClient oauthClient;
    private final GitHubUserProfileClient userProfileClient;
    private final GitHubAppAuthorizationStore authorizationStore;
    private final GitHubTokenCipher tokenCipher;
    private final SecureRandom secureRandom = new SecureRandom();

    public GitHubAuthStatusResponse status(HttpServletRequest request, HttpServletResponse response) {
        var mode = copilotProperties.getAuth().getMode();
        if (mode == CopilotAuthMode.LOCAL_TOKEN) {
            return new GitHubAuthStatusResponse(
                    mode.name(),
                    false,
                    true,
                    null,
                    localDisplayName(),
                    null,
                    false,
                    null
            );
        }

        var operatorSessionId = operatorSessionService.getOrCreateSessionId(request, response);
        var authorization = authorizationStore.findActiveByOperatorSessionId(operatorSessionId).orElse(null);
        if (authorization == null) {
            return disconnectedStatus(mode);
        }

        var reauthRequired = reauthRequired(authorization);
        return new GitHubAuthStatusResponse(
                mode.name(),
                true,
                !reauthRequired,
                reauthRequired ? null : authorization.githubLogin(),
                reauthRequired ? null : authorization.githubLogin(),
                authorization.accessTokenExpiresAt(),
                reauthRequired,
                AUTH_START_URL
        );
    }

    public URI start(String returnUrl, HttpServletRequest request, HttpServletResponse response) {
        requireGithubAppMode();
        requireConfigured();

        var safeReturnUrl = validateReturnUrl(returnUrl);
        var operatorSessionId = operatorSessionService.getOrCreateSessionId(request, response);
        var state = randomUrlToken(32);
        var pkce = pkce();
        var now = Instant.now();

        stateStore.save(new GitHubOAuthState(
                state,
                operatorSessionId,
                safeReturnUrl,
                now,
                now.plus(githubProperties.getOauthStateTtl()),
                pkce.verifier()
        ));

        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", githubProperties.getClientId())
                .queryParam("redirect_uri", githubProperties.getCallbackUrl())
                .queryParam("state", state)
                .queryParam("code_challenge", pkce.challenge())
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUri();
    }

    public URI callback(
            String code,
            String state,
            String error,
            HttpServletRequest request
    ) {
        requireGithubAppMode();
        if (StringUtils.hasText(error)) {
            throw new GitHubOAuthExchangeException("GitHub OAuth authorization failed: " + error.trim());
        }
        if (!StringUtils.hasText(code)) {
            throw new GitHubOAuthExchangeException("GitHub OAuth callback did not include code.");
        }

        var operatorSessionId = operatorSessionService.currentSessionId(request)
                .orElseThrow(() -> new GitHubOAuthStateInvalidException("Operator session cookie is missing."));
        var storedState = stateStore.consume(state, operatorSessionId, Instant.now());
        var tokenResponse = oauthClient.exchangeCode(code, storedState.codeVerifier());
        var profile = userProfileClient.currentUser(tokenResponse.accessToken());
        userProfileClient.verifyRequiredOrg(tokenResponse.accessToken(), githubProperties.getRequiredOrg());

        var now = Instant.now();
        authorizationStore.save(new GitHubAppAuthorization(
                operatorSessionId,
                profile.id(),
                profile.login(),
                tokenCipher.encrypt(tokenResponse.accessToken()),
                expiresAt(now, tokenResponse.expiresIn()),
                tokenCipher.encrypt(tokenResponse.refreshToken()),
                expiresAt(now, tokenResponse.refreshTokenExpiresIn()),
                now,
                now,
                now,
                null
        ));

        return URI.create(appendQueryParam(storedState.returnUrl(), "githubAuth=connected"));
    }

    public void logout() {
        operatorSessionService.currentSessionId()
                .ifPresent(sessionId -> {
                    try {
                        authorizationStore.revoke(sessionId);
                    } catch (RuntimeException ignored) {
                        // Logout is idempotent from the operator perspective.
                    }
                });
    }

    private GitHubAuthStatusResponse disconnectedStatus(CopilotAuthMode mode) {
        return new GitHubAuthStatusResponse(
                mode.name(),
                true,
                false,
                null,
                null,
                null,
                true,
                AUTH_START_URL
        );
    }

    private boolean reauthRequired(GitHubAppAuthorization authorization) {
        return authorization.accessTokenExpiresAt() != null
                && authorization.accessTokenExpiresAt().isBefore(Instant.now())
                && !StringUtils.hasText(authorization.encryptedRefreshToken());
    }

    private void requireGithubAppMode() {
        if (copilotProperties.getAuth().getMode() != CopilotAuthMode.GITHUB_APP) {
            throw new GitHubOAuthExchangeException("GitHub App OAuth is disabled in LOCAL_TOKEN mode.");
        }
    }

    private void requireConfigured() {
        if (!githubProperties.hasOAuthClientConfiguration()) {
            throw new GitHubOAuthExchangeException("GitHub App OAuth client id, secret and callback URL must be configured.");
        }
        if (!StringUtils.hasText(githubProperties.getTokenEncryptionKey())) {
            throw new GitHubOAuthExchangeException("analysis.github-app.token-encryption-key must be configured in GITHUB_APP mode.");
        }
    }

    private String validateReturnUrl(String returnUrl) {
        if (!StringUtils.hasText(returnUrl)) {
            return "/";
        }

        var trimmed = returnUrl.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.contains("://")) {
            throw new GitHubOAuthExchangeException("Invalid local returnUrl for GitHub OAuth.");
        }

        return trimmed;
    }

    private String appendQueryParam(String returnUrl, String queryParam) {
        return returnUrl.contains("?") ? returnUrl + "&" + queryParam : returnUrl + "?" + queryParam;
    }

    private String localDisplayName() {
        var local = copilotProperties.getAuth().getLocal();
        return local != null && StringUtils.hasText(local.getDisplayName())
                ? local.getDisplayName().trim()
                : "Local developer token";
    }

    private GitHubOAuthPkce pkce() {
        var verifier = randomUrlToken(32);
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            var challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return new GitHubOAuthPkce(verifier, challenge);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate GitHub OAuth PKCE challenge.", exception);
        }
    }

    private String randomUrlToken(int byteCount) {
        var bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Instant expiresAt(Instant now, Long expiresInSeconds) {
        return expiresInSeconds != null ? now.plusSeconds(expiresInSeconds) : null;
    }
}
