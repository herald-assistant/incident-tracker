package pl.mkn.tdw.integrations.github.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class GitHubUserProfileClient {

    private final RestClient.Builder restClientBuilder;

    public GitHubUserProfile currentUser(String accessToken) {
        try {
            var response = restClient(accessToken)
                    .get()
                    .uri("https://api.github.com/user")
                    .retrieve()
                    .body(GitHubUserProfileResponse.class);

            if (response == null || response.id() == null || !StringUtils.hasText(response.login())) {
                throw new GitHubOAuthExchangeException("GitHub user profile response is missing id or login.");
            }

            return new GitHubUserProfile(response.id(), response.login());
        } catch (RestClientResponseException exception) {
            throw new GitHubOAuthExchangeException(
                    "GitHub user profile lookup failed with HTTP status " + exception.getStatusCode().value() + ".",
                    exception
            );
        }
    }

    public void verifyRequiredOrg(String accessToken, String org) {
        if (!StringUtils.hasText(org)) {
            return;
        }

        try {
            restClient(accessToken)
                    .get()
                    .uri("https://api.github.com/user/memberships/orgs/{org}", org.trim())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new GitHubOAuthExchangeException(
                    "Nie można potwierdzić członkostwa GitHub w wymaganej organizacji.",
                    exception
            );
        }
    }

    private RestClient restClient(String accessToken) {
        return restClientBuilder.clone()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
    }

    private record GitHubUserProfileResponse(
            Long id,
            @JsonProperty("login")
            String login
    ) {
    }
}
