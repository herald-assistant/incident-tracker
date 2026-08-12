package pl.mkn.tdw.testsupport.copilot;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigFactory;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class CopilotSessionConfigFactoryTestCreator {

    private CopilotSessionConfigFactoryTestCreator() {
    }

    public static CopilotSessionConfigFactory create(CopilotSdkProperties properties) {
        var skillRuntimeLoader = mock(CopilotSkillRuntimeLoader.class);
        when(skillRuntimeLoader.platformSkillDirectories()).thenAnswer(ignored ->
                List.of(properties.resolvedSkillDirectory().toString())
        );
        return new CopilotSessionConfigFactory(
                properties,
                auth -> new CopilotAccessToken(testCompatibleToken(properties), null, null, false),
                skillRuntimeLoader
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
