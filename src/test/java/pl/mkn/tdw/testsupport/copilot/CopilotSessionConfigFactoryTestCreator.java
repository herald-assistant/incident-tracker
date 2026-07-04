package pl.mkn.tdw.testsupport.copilot;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigFactory;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken;

public final class CopilotSessionConfigFactoryTestCreator {

    private CopilotSessionConfigFactoryTestCreator() {
    }

    public static CopilotSessionConfigFactory create(CopilotSdkProperties properties) {
        return new CopilotSessionConfigFactory(
                properties,
                auth -> new CopilotAccessToken(testCompatibleToken(properties), null, null, false)
        );
    }

    private static String testCompatibleToken(CopilotSdkProperties properties) {
        if (properties.getAuth() != null
                && properties.getAuth().getLocal() != null
                && StringUtils.hasText(properties.getAuth().getLocal().getGithubToken())) {
            return properties.getAuth().getLocal().getGithubToken();
        }
        if (StringUtils.hasText(properties.getGithubToken())) {
            return properties.getGithubToken();
        }
        return "test-token";
    }
}
