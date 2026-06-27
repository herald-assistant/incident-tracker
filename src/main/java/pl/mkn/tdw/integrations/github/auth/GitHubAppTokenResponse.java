package pl.mkn.tdw.integrations.github.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;

public record GitHubAppTokenResponse(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("expires_in")
        Long expiresIn,
        @JsonProperty("refresh_token")
        String refreshToken,
        @JsonProperty("refresh_token_expires_in")
        Long refreshTokenExpiresIn,
        @JsonProperty("token_type")
        String tokenType,
        String scope,
        String error,
        @JsonProperty("error_description")
        String errorDescription
) {

    public boolean successful() {
        return StringUtils.hasText(accessToken) && !StringUtils.hasText(error);
    }

    public String safeErrorMessage() {
        if (StringUtils.hasText(errorDescription)) {
            return error + ": " + errorDescription;
        }
        if (StringUtils.hasText(error)) {
            return error;
        }
        return "GitHub OAuth did not return an access token.";
    }
}
