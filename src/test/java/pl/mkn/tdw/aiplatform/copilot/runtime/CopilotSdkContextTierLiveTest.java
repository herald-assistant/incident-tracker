package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.ResumeSessionConfig;
import com.github.copilot.rpc.SessionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "COPILOT_SDK_LIVE_TEST", matches = "(?i)true")
class CopilotSdkContextTierLiveTest {

    @TempDir
    Path copilotHome;

    @Test
    void shouldCreateAbortResumeWithLongContextAndReadCurrentModel() {
        var sessionId = "tdw-context-tier-live-" + UUID.randomUUID();
        var cliPath = new CopilotCliExecutableResolver().resolve(
                System.getenv().getOrDefault("COPILOT_CLI_PATH", "copilot"),
                System.getProperty("user.dir")
        );
        var options = new CopilotClientOptions()
                .setCliPath(cliPath)
                .setCwd(System.getProperty("user.dir"))
                .setCopilotHome(copilotHome.toString());
        var token = System.getenv("COPILOT_GITHUB_TOKEN");
        if (StringUtils.hasText(token)) {
            options.setUseLoggedInUser(false).setGitHubToken(token);
        } else {
            options.setUseLoggedInUser(true);
        }

        try (var client = new CopilotClient(options)) {
            client.start().join();
            var runtime = new CopilotRuntimeCompatibility().inspect(client);
            assertThat(runtime.compatible()).isTrue();

            var createConfig = new SessionConfig()
                    .setSessionId(sessionId)
                    .setOnPermissionRequest(PermissionHandler.APPROVE_ALL);
            setModel(createConfig);
            try (var created = client.createSession(createConfig).join()) {
                created.abort().join();
            }

            var resumeConfig = new ResumeSessionConfig()
                    .setContextTier("long_context")
                    .setOnPermissionRequest(PermissionHandler.APPROVE_ALL);
            setModel(resumeConfig);
            try (var resumed = client.resumeSession(sessionId, resumeConfig).join()) {
                var current = resumed.getRpc().model.getCurrent().join();
                assertThat(current.contextTier()).isNotNull();
                assertThat(current.contextTier().getValue()).isEqualTo("long_context");
            } finally {
                client.deleteSession(sessionId).join();
            }
        }
    }

    private void setModel(SessionConfig config) {
        var model = System.getenv("COPILOT_TEST_MODEL");
        if (StringUtils.hasText(model)) {
            config.setModel(model.trim());
        }
    }

    private void setModel(ResumeSessionConfig config) {
        var model = System.getenv("COPILOT_TEST_MODEL");
        if (StringUtils.hasText(model)) {
            config.setModel(model.trim());
        }
    }
}
