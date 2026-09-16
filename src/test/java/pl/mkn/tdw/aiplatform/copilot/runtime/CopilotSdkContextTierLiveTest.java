package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import com.github.copilot.generated.SessionUsageInfoEvent;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.ResumeSessionConfig;
import com.github.copilot.rpc.SessionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotContextTierActivator;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "COPILOT_SDK_LIVE_TEST", matches = "(?i)true")
class CopilotSdkContextTierLiveTest {

    @TempDir
    Path copilotHome;

    @Test
    void shouldIncreaseActualWindowAfterResumeAndExplicitLongContextActivation() throws Exception {
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
            var baselineLimit = new AtomicLong();
            try (var created = client.createSession(createConfig).join()) {
                try (var ignored = created.on(event -> captureTokenLimit(event, baselineLimit))) {
                    var response = created.sendAndWait(new MessageOptions().setPrompt(
                            "Odpowiedz jednym zdaniem: jaki jest cel fikcyjnego systemu CRM do obsługi kontaktów?"
                    ), 120_000L).join();
                    assertThat(response).isNotNull();
                    assertThat(baselineLimit.get()).isPositive();
                }
                created.abort().join();
            }

            var resumeConfig = new ResumeSessionConfig()
                    .setContextTier("long_context")
                    .setOnPermissionRequest(PermissionHandler.APPROVE_ALL);
            setModel(resumeConfig);
            var upgradedLimit = new AtomicLong();
            try (var resumed = client.resumeSession(sessionId, resumeConfig).join()) {
                assertThat(new CopilotContextTierActivator().activateLongContext(resumed, 20_000L)).isTrue();
                var current = resumed.getRpc().model.getCurrent().join();
                assertThat(current.contextTier()).isNotNull();
                assertThat(current.contextTier().getValue()).isEqualTo("long_context");
                try (var ignored = resumed.on(event -> captureTokenLimit(event, upgradedLimit))) {
                    var response = resumed.sendAndWait(new MessageOptions().setPrompt(
                            "Kontynuuj i podaj jeden neutralny przykład procesu CRM."
                    ), 120_000L).join();
                    assertThat(response).isNotNull();
                }
                assertThat(upgradedLimit.get()).isGreaterThan(baselineLimit.get());
                assertThat(resumed.getSessionId()).isEqualTo(sessionId);
            } finally {
                client.deleteSession(sessionId).join();
            }
        }
    }

    private void captureTokenLimit(Object event, AtomicLong tokenLimit) {
        if (event instanceof SessionUsageInfoEvent usage && usage.getData() != null) {
            tokenLimit.set(usage.getData().tokenLimit());
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
