package pl.mkn.tdw.testsupport.copilot;

import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotCliExecutableResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigFactory;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

public final class CopilotSessionConfigFactoryTestCreator {

    private CopilotSessionConfigFactoryTestCreator() {
    }

    public static CopilotSessionConfigFactory create(CopilotSdkProperties properties) {
        return create(properties, "github_pat_crm_test_token");
    }

    public static CopilotSessionConfigFactory create(CopilotSdkProperties properties, String token) {
        var skillRuntimeLoader = mock(CopilotSkillRuntimeLoader.class);
        var cliExecutableResolver = mock(CopilotCliExecutableResolver.class);
        when(skillRuntimeLoader.platformSkillDirectories()).thenAnswer(ignored ->
                List.of(properties.resolvedSkillDirectory().toString())
        );
        when(cliExecutableResolver.resolve(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new CopilotSessionConfigFactory(
                properties,
                auth -> new CopilotAccessToken(token, null, null, true),
                skillRuntimeLoader,
                cliExecutableResolver
        );
    }

}
