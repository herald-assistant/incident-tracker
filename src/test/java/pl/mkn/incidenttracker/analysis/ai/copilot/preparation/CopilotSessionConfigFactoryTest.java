package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequest;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import com.github.copilot.sdk.json.PreToolUseHookInput;
import com.github.copilot.sdk.json.ToolDefinition;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.copilot.coverage.CopilotEvidenceCoverageReport;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CopilotSessionConfigFactoryTest {

    @Test
    void shouldBuildClientOptionsAndSessionConfig() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setCliPath("C:\\tools\\copilot.exe");
        properties.setClientName("incidenttracker-test");
        properties.setModel("gpt-5.4");
        properties.setReasoningEffort("medium");
        properties.setDisabledSkills(List.of("incident-analysis-gitlab-tools"));
        var factory = new CopilotSessionConfigFactory(properties);
        var tools = tools("gitlab_find_flow_context", "gitlab_read_repository_file_chunk");
        var policy = new CopilotToolAccessPolicy(
                tools,
                List.of("gitlab_find_flow_context"),
                true,
                false,
                true,
                false,
                CopilotEvidenceCoverageReport.empty()
        );

        var clientOptions = factory.clientOptions();
        var sessionConfig = factory.sessionConfig(
                context(),
                tools,
                policy,
                List.of("C:\\runtime\\copilot_skills")
        );

        assertEquals("C:\\tools\\copilot.exe", clientOptions.getCliPath());
        assertEquals("C:\\workspace", clientOptions.getCwd());
        assertEquals(Boolean.TRUE, clientOptions.getUseLoggedInUser());
        assertEquals(null, clientOptions.getGithubToken());
        assertEquals("analysis-123", sessionConfig.getSessionId());
        assertEquals("incidenttracker-test", sessionConfig.getClientName());
        assertEquals("C:\\workspace", sessionConfig.getWorkingDirectory());
        assertFalse(sessionConfig.isStreaming());
        assertEquals(tools, sessionConfig.getTools());
        assertEquals(List.of("gitlab_find_flow_context"), sessionConfig.getAvailableTools());
        assertEquals(List.of("C:\\runtime\\copilot_skills"), sessionConfig.getSkillDirectories());
        assertEquals(List.of("incident-analysis-gitlab-tools"), sessionConfig.getDisabledSkills());
        assertEquals("gpt-5.4", sessionConfig.getModel());
        assertEquals("medium", sessionConfig.getReasoningEffort());
        assertEquals(PermissionHandler.APPROVE_ALL, sessionConfig.getOnPermissionRequest());
        assertNotNull(sessionConfig.getHooks());

        var allowedToolDecision = sessionConfig.getHooks().getOnPreToolUse()
                .handle(new PreToolUseHookInput().setToolName("gitlab_find_flow_context"), null)
                .join();
        var deniedToolDecision = sessionConfig.getHooks().getOnPreToolUse()
                .handle(new PreToolUseHookInput().setToolName("read_file"), null)
                .join();

        assertEquals("allow", allowedToolDecision.permissionDecision());
        assertEquals("deny", deniedToolDecision.permissionDecision());
    }

    @Test
    void shouldUseGithubTokenWhenProvided() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setGithubToken("ghp_test_token");
        var factory = new CopilotSessionConfigFactory(properties);

        var clientOptions = factory.clientOptions();

        assertEquals("ghp_test_token", clientOptions.getGithubToken());
        assertEquals(Boolean.FALSE, clientOptions.getUseLoggedInUser());
    }

    @Test
    void shouldConfigureDenyAllPermissionHandlerWhenRequested() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setPermissionMode(CopilotSdkProperties.PermissionMode.DENY_ALL);
        var factory = new CopilotSessionConfigFactory(properties);

        var sessionConfig = factory.sessionConfig(
                context(),
                List.of(),
                CopilotToolAccessPolicy.empty(),
                null
        );

        var decision = sessionConfig.getOnPermissionRequest()
                .handle(new PermissionRequest(), null)
                .join();

        assertEquals(PermissionRequestResultKind.DENIED_BY_RULES.toString(), decision.getKind());
        assertEquals(List.of(), sessionConfig.getSkillDirectories());
        assertEquals(List.of(), sessionConfig.getDisabledSkills());
    }

    private CopilotToolSessionContext context() {
        return new CopilotToolSessionContext(
                "run-123",
                "analysis-123",
                "corr-123",
                "dev3",
                "main",
                "sample/runtime"
        );
    }

    private List<ToolDefinition> tools(String... names) {
        return List.of(names).stream().map(name -> ToolDefinition.createSkipPermission(
                name,
                name,
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        )).toList();
    }
}
