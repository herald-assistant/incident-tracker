package pl.mkn.incidenttracker.integrations.github.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class GitHubAppOAuthClient {

    private static final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";

    private final GitHubAppAuthProperties properties;
    private final RestClient.Builder restClientBuilder;

    public GitHubAppTokenResponse exchangeCode(String code, String codeVerifier) {
        var form = baseForm();
        form.add("code", code);
        form.add("redirect_uri", properties.getCallbackUrl());
        if (StringUtils.hasText(codeVerifier)) {
            form.add("code_verifier", codeVerifier);
        }

        return requestToken(form);
    }

    public GitHubAppTokenResponse refresh(String refreshToken) {
        var form = baseForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);

        return requestToken(form);
    }

    private LinkedMultiValueMap<String, String> baseForm() {
        if (!properties.hasOAuthClientConfiguration()) {
            throw new GitHubOAuthExchangeException("GitHub App OAuth client id, secret and callback URL must be configured.");
        }

        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        return form;
    }

    private GitHubAppTokenResponse requestToken(LinkedMultiValueMap<String, String> form) {
        try {
            var response = restClientBuilder.clone()
                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri(ACCESS_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GitHubAppTokenResponse.class);

            if (response == null || !response.successful()) {
                throw new GitHubOAuthExchangeException(
                        response != null ? response.safeErrorMessage() : "GitHub OAuth returned an empty response."
                );
            }

            return response;
        } catch (RestClientResponseException exception) {
            throw new GitHubOAuthExchangeException(
                    "GitHub OAuth token exchange failed with HTTP status " + exception.getStatusCode().value() + ".",
                    exception
            );
        }
    }
}
