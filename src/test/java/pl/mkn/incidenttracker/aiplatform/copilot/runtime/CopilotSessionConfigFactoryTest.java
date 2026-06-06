package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.PermissionRequest;
import com.github.copilot.rpc.PermissionRequestResultKind;
import com.github.copilot.rpc.PreToolUseHookInput;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;

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

        var clientOptions = factory.clientOptions();
        var sessionConfigRequest = new CopilotSessionConfigRequest(
                sessionId(),
                tools,
                List.of("gitlab_find_flow_context"),
                List.of("C:\\runtime\\copilot_skills"),
                CopilotModelSelection.DEFAULT,
                "Use only the enabled test tools."
        );
        var sessionConfig = factory.sessionConfig(sessionConfigRequest);

        assertEquals(List.of("gitlab_find_flow_context", "skill"), sessionConfigRequest.effectiveAvailableToolNames());
        assertEquals(true, sessionConfigRequest.skillToolAvailable());
        assertEquals(true, sessionConfigRequest.skillDirectoriesConfigured());
        assertEquals("C:\\tools\\copilot.exe", clientOptions.getCliPath());
        assertEquals("C:\\workspace", clientOptions.getCwd());
        assertEquals(Boolean.FALSE, clientOptions.getUseLoggedInUser().orElseThrow());
        assertEquals("test-token", clientOptions.getGithubToken());
        assertEquals("analysis-123", sessionConfig.getSessionId());
        assertEquals("incidenttracker-test", sessionConfig.getClientName());
        assertEquals("C:\\workspace", sessionConfig.getWorkingDirectory());
        assertFalse(sessionConfig.isStreaming());
        assertEquals(tools, sessionConfig.getTools());
        assertEquals(List.of("gitlab_find_flow_context", "skill"), sessionConfig.getAvailableTools());
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
        var skillToolDecision = sessionConfig.getHooks().getOnPreToolUse()
                .handle(new PreToolUseHookInput().setToolName("skill"), null)
                .join();

        assertEquals("allow", allowedToolDecision.permissionDecision());
        assertEquals("deny", deniedToolDecision.permissionDecision());
        assertEquals("allow", skillToolDecision.permissionDecision());
    }

    @Test
    void shouldPreferRequestAiOptionsOverConfiguredDefaults() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setModel("gpt-5.4");
        properties.setReasoningEffort("medium");
        var factory = new CopilotSessionConfigFactory(properties);

        var sessionConfig = factory.sessionConfig(new CopilotSessionConfigRequest(
                sessionId(),
                List.of(),
                List.of(),
                List.of(),
                new CopilotModelSelection("gpt-5.3-codex", "high"),
                null
        ));

        assertEquals("gpt-5.3-codex", sessionConfig.getModel());
        assertEquals("high", sessionConfig.getReasoningEffort());
    }

    @Test
    void shouldUseGithubTokenWhenProvided() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setGithubToken("ghp_test_token");
        var factory = new CopilotSessionConfigFactory(properties);

        var clientOptions = factory.clientOptions();

        assertEquals("ghp_test_token", clientOptions.getGithubToken());
        assertEquals(Boolean.FALSE, clientOptions.getUseLoggedInUser().orElseThrow());
    }

    @Test
    void shouldConfigureDenyAllPermissionHandlerWhenRequested() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setPermissionMode(CopilotSdkProperties.PermissionMode.DENY_ALL);
        var factory = new CopilotSessionConfigFactory(properties);

        var sessionConfig = factory.sessionConfig(new CopilotSessionConfigRequest(
                sessionId(),
                List.of(),
                List.of(),
                null,
                CopilotModelSelection.DEFAULT,
                null
        ));

        var decision = sessionConfig.getOnPermissionRequest()
                .handle(new PermissionRequest(), null)
                .join();

        assertEquals(PermissionRequestResultKind.DENIED_BY_RULES.toString(), decision.getKind());
        assertEquals(List.of(), sessionConfig.getAvailableTools());
        assertEquals(List.of(), sessionConfig.getSkillDirectories());
        assertEquals(List.of(), sessionConfig.getDisabledSkills());
    }

    private String sessionId() {
        return "analysis-123";
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
