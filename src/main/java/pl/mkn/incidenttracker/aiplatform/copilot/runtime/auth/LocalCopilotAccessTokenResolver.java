package pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;

@Component
@RequiredArgsConstructor
public class LocalCopilotAccessTokenResolver implements CopilotAccessTokenResolver {

    private final CopilotSdkProperties properties;

    @Override
    public CopilotAccessToken resolve(CopilotRunAuth auth) {
        var token = configuredToken();
        if (!StringUtils.hasText(token)) {
            throw new CopilotLocalTokenMissingException();
        }

        return new CopilotAccessToken(token.trim(), null, null, false);
    }

    private String configuredToken() {
        if (properties.getAuth() != null
                && properties.getAuth().getLocal() != null
                && StringUtils.hasText(properties.getAuth().getLocal().getGithubToken())) {
            return properties.getAuth().getLocal().getGithubToken();
        }

        return properties.getGithubToken();
    }
}
