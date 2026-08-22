package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.SystemMessageMode;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.PermissionRequest;
import com.github.copilot.rpc.PermissionRequestResultKind;
import com.github.copilot.rpc.PreToolUseHookInput;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import pl.mkn.tdw.testsupport.copilot.CopilotSessionConfigFactoryTestCreator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CopilotSessionConfigFactoryTest {

    @Test
    void shouldBuildClientOptionsAndSessionConfig() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setCopilotHome("C:\\tdw-data\\copilot");
        properties.setCliPath("C:\\tools\\copilot.exe");
        properties.setClientName("incidenttracker-test");
        properties.setModel("gpt-5.4");
        properties.setReasoningEffort("medium");
        properties.setDisabledSkills(List.of("incident-code-grounding"));
        var factory = CopilotSessionConfigFactoryTestCreator.create(properties);
        var tools = tools("gitlab_find_flow_context", "gitlab_read_repository_file_chunk");

        var clientOptions = factory.clientOptions();
        var sessionConfigRequest = new CopilotSessionConfigRequest(
                sessionId(),
                tools,
                List.of("gitlab_find_flow_context"),
                CopilotModelSelection.DEFAULT,
                "Use only the enabled test tools."
        );
        var sessionConfig = factory.sessionConfig(sessionConfigRequest);
        var resumeSessionConfig = factory.resumeSessionConfig(sessionConfigRequest);

        assertEquals(List.of("gitlab_find_flow_context", "skill"), sessionConfigRequest.effectiveAvailableToolNames());
        assertEquals(true, sessionConfigRequest.skillToolAvailable());
        assertEquals("C:\\tools\\copilot.exe", clientOptions.getCliPath());
        assertEquals("C:\\workspace", clientOptions.getCwd());
        assertEquals(normalized("C:\\tdw-data\\copilot"), clientOptions.getCopilotHome());
        assertEquals(Boolean.FALSE, clientOptions.getUseLoggedInUser().orElseThrow());
        assertEquals("test-token", clientOptions.getGithubToken());
        assertEquals("analysis-123", sessionConfig.getSessionId());
        assertEquals("incidenttracker-test", sessionConfig.getClientName());
        assertEquals("C:\\workspace", sessionConfig.getWorkingDirectory());
        assertFalse(sessionConfig.isStreaming());
        assertEquals(tools, sessionConfig.getTools());
        assertEquals(List.of("gitlab_find_flow_context", "skill"), sessionConfig.getAvailableTools());
        assertEquals(
                List.of(normalized("C:\\tdw-data\\copilot\\skills")),
                sessionConfig.getSkillDirectories()
        );
        assertEquals(List.of("incident-code-grounding"), sessionConfig.getDisabledSkills());
        assertEquals("gpt-5.4", sessionConfig.getModel());
        assertEquals("medium", sessionConfig.getReasoningEffort());
        assertNull(sessionConfig.getSystemMessage());
        assertNull(sessionConfig.getInfiniteSessions());
        assertEquals(PermissionHandler.APPROVE_ALL, sessionConfig.getOnPermissionRequest());
        assertNotNull(sessionConfig.getHooks());
        assertEquals("incidenttracker-test", resumeSessionConfig.getClientName());
        assertEquals("C:\\workspace", resumeSessionConfig.getWorkingDirectory());
        assertFalse(resumeSessionConfig.isStreaming());
        assertEquals(tools, resumeSessionConfig.getTools());
        assertEquals(List.of("gitlab_find_flow_context", "skill"), resumeSessionConfig.getAvailableTools());
        assertEquals(
                List.of(normalized("C:\\tdw-data\\copilot\\skills")),
                resumeSessionConfig.getSkillDirectories()
        );
        assertEquals(List.of("incident-code-grounding"), resumeSessionConfig.getDisabledSkills());
        assertEquals("gpt-5.4", resumeSessionConfig.getModel());
        assertEquals("medium", resumeSessionConfig.getReasoningEffort());
        assertNull(resumeSessionConfig.getSystemMessage());
        assertNull(resumeSessionConfig.getInfiniteSessions());
        assertEquals(PermissionHandler.APPROVE_ALL, resumeSessionConfig.getOnPermissionRequest());
        assertNotNull(resumeSessionConfig.getHooks());

        var allowedToolDecision = sessionConfig.getHooks().getOnPreToolUse()
                .handle(new PreToolUseHookInput().setToolName("gitlab_find_flow_context"), null)
                .join();
        var deniedToolDecision = sessionConfig.getHooks().getOnPreToolUse()
                .handle(new PreToolUseHookInput().setToolName("read_file"), null)
                .join();
        var skillToolDecision = sessionConfig.getHooks().getOnPreToolUse()
                .handle(new PreToolUseHookInput().setToolName("skill"), null)
                .join();
        var resumeDeniedToolDecision = resumeSessionConfig.getHooks().getOnPreToolUse()
                .handle(new PreToolUseHookInput().setToolName("read_file"), null)
                .join();

        assertEquals("allow", allowedToolDecision.permissionDecision());
        assertEquals("deny", deniedToolDecision.permissionDecision());
        assertEquals("allow", skillToolDecision.permissionDecision());
        assertEquals("deny", resumeDeniedToolDecision.permissionDecision());
    }

    @Test
    void shouldAppendDurableCrmInstructionsToNewAndResumedSession() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        var factory = CopilotSessionConfigFactoryTestCreator.create(properties);
        var instructions = "Synthetic CRM response contract that must survive session compaction.";
        var request = new CopilotSessionConfigRequest(
                sessionId(),
                List.of(),
                List.of(),
                CopilotModelSelection.DEFAULT,
                null
        ).withDurableSystemInstructions(instructions);

        var sessionConfig = factory.sessionConfig(request);
        var resumeSessionConfig = factory.resumeSessionConfig(request);

        assertEquals(SystemMessageMode.APPEND, sessionConfig.getSystemMessage().getMode());
        assertEquals(instructions, sessionConfig.getSystemMessage().getContent());
        assertEquals(SystemMessageMode.APPEND, resumeSessionConfig.getSystemMessage().getMode());
        assertEquals(instructions, resumeSessionConfig.getSystemMessage().getContent());
    }

    @Test
    void shouldAllowOneShotSessionToDisableTheBuiltInSkillTool() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        var factory = CopilotSessionConfigFactoryTestCreator.create(properties);
        var request = new CopilotSessionConfigRequest(
                sessionId(),
                List.of(),
                List.of(),
                CopilotModelSelection.DEFAULT,
                "No tools are available for this one-shot session.",
                false
        );

        var sessionConfig = factory.sessionConfig(request);
        var resumeSessionConfig = factory.resumeSessionConfig(request);
        var deniedSkillDecision = sessionConfig.getHooks().getOnPreToolUse()
                .handle(new PreToolUseHookInput().setToolName("skill"), null)
                .join();
        var resumeDeniedSkillDecision = resumeSessionConfig.getHooks().getOnPreToolUse()
                .handle(new PreToolUseHookInput().setToolName("skill"), null)
                .join();

        assertEquals(List.of(), request.effectiveAvailableToolNames());
        assertFalse(request.skillToolAvailable());
        assertEquals(List.of(), sessionConfig.getAvailableTools());
        assertEquals(List.of(), sessionConfig.getSkillDirectories());
        assertEquals(List.of(), resumeSessionConfig.getAvailableTools());
        assertEquals(List.of(), resumeSessionConfig.getSkillDirectories());
        assertEquals("deny", deniedSkillDecision.permissionDecision());
        assertEquals("deny", resumeDeniedSkillDecision.permissionDecision());
    }

    @Test
    void shouldRejectBlankCopilotHome() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setCopilotHome(" ");
        var factory = CopilotSessionConfigFactoryTestCreator.create(properties);

        assertThrows(IllegalStateException.class, factory::clientOptions);
    }

    @Test
    void shouldPreferRequestAiOptionsOverConfiguredDefaults() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setModel("gpt-5.4");
        properties.setReasoningEffort("medium");
        var factory = CopilotSessionConfigFactoryTestCreator.create(properties);

        var sessionConfig = factory.sessionConfig(new CopilotSessionConfigRequest(
                sessionId(),
                List.of(),
                List.of(),
                new CopilotModelSelection("gpt-5.3-codex", "high"),
                null
        ));

        assertEquals("gpt-5.3-codex", sessionConfig.getModel());
        assertEquals("high", sessionConfig.getReasoningEffort());
    }

    @Test
    void shouldNotApplyConfiguredReasoningEffortToExplicitModelWithoutOverride() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setModel("gpt-5.4");
        properties.setReasoningEffort("medium");
        var factory = CopilotSessionConfigFactoryTestCreator.create(properties);
        var request = new CopilotSessionConfigRequest(
                sessionId(),
                List.of(),
                List.of(),
                new CopilotModelSelection("gpt-basic", null),
                null
        );

        var sessionConfig = factory.sessionConfig(request);
        var resumeSessionConfig = factory.resumeSessionConfig(request);

        assertEquals("gpt-basic", sessionConfig.getModel());
        assertNull(sessionConfig.getReasoningEffort());
        assertEquals("gpt-basic", resumeSessionConfig.getModel());
        assertNull(resumeSessionConfig.getReasoningEffort());
    }

    @Test
    void shouldUseGithubTokenWhenProvided() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setGithubToken("ghp_test_token");
        var factory = CopilotSessionConfigFactoryTestCreator.create(properties);

        var clientOptions = factory.clientOptions();

        assertEquals("ghp_test_token", clientOptions.getGithubToken());
        assertEquals(Boolean.FALSE, clientOptions.getUseLoggedInUser().orElseThrow());
    }

    @Test
    void shouldConfigureDenyAllPermissionHandlerWhenRequested() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setPermissionMode(CopilotSdkProperties.PermissionMode.DENY_ALL);
        var factory = CopilotSessionConfigFactoryTestCreator.create(properties);

        var sessionConfig = factory.sessionConfig(new CopilotSessionConfigRequest(
                sessionId(),
                List.of(),
                List.of(),
                CopilotModelSelection.DEFAULT,
                null
        ));

        var decision = sessionConfig.getOnPermissionRequest()
                .handle(new PermissionRequest(), null)
                .join();

        assertEquals(PermissionRequestResultKind.DENIED_BY_RULES.toString(), decision.getKind());
        assertEquals(List.of("skill"), sessionConfig.getAvailableTools());
        assertEquals(
                List.of(properties.resolvedSkillDirectory().toString()),
                sessionConfig.getSkillDirectories()
        );
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

    private String normalized(String value) {
        return Path.of(value).toAbsolutePath().normalize().toString();
    }
}
