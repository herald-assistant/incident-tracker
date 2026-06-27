package pl.mkn.tdw.integrations.github.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.github-app")
public class GitHubAppAuthProperties {

    private String clientId;
    private String clientSecret;
    private String callbackUrl = "http://localhost:8080/api/auth/github/callback";
    private String requiredOrg;
    private Duration tokenRefreshSkew = Duration.ofMinutes(5);
    private String cookieName = "incident_tracker_operator";
    private boolean cookieSecure;
    private String cookieSameSite = "lax";
    private Duration cookieMaxAge = Duration.ofDays(30);
    private Duration oauthStateTtl = Duration.ofMinutes(10);
    private String tokenEncryptionKey;

    public boolean hasOAuthClientConfiguration() {
        return StringUtils.hasText(clientId)
                && StringUtils.hasText(clientSecret)
                && StringUtils.hasText(callbackUrl);
    }
}
