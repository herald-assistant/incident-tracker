package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequestResult;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import com.github.copilot.sdk.json.PreToolUseHookOutput;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SessionHooks;
import com.github.copilot.sdk.json.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiOptions;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class CopilotSessionConfigFactory {

    private final CopilotSdkProperties properties;

    public CopilotClientOptions clientOptions() {
        var clientOptions = new CopilotClientOptions()
                .setUseLoggedInUser(true)
                .setCwd(properties.getWorkingDirectory());

        if (properties.getCliPath() != null && !properties.getCliPath().isBlank()) {
            clientOptions.setCliPath(properties.getCliPath());
        }

        if (properties.getGithubToken() != null && !properties.getGithubToken().isBlank()) {
            clientOptions
                    .setGitHubToken(properties.getGithubToken())
                    .setUseLoggedInUser(false);
        }

        return clientOptions;
    }

    public SessionConfig sessionConfig(
            CopilotToolSessionContext context,
            List<ToolDefinition> tools,
            CopilotToolAccessPolicy policy,
            List<String> skillDirectories
    ) {
        return sessionConfig(context, tools, policy, skillDirectories, AnalysisAiOptions.DEFAULT);
    }

    public SessionConfig sessionConfig(
            CopilotToolSessionContext context,
            List<ToolDefinition> tools,
            CopilotToolAccessPolicy policy,
            List<String> skillDirectories,
            AnalysisAiOptions options
    ) {
        var sessionConfig = new SessionConfig()
                .setSessionId(context.copilotSessionId())
                .setClientName(properties.getClientName())
                .setWorkingDirectory(properties.getWorkingDirectory())
                .setStreaming(false)
                .setTools(safeList(tools))
                .setAvailableTools(policy.availableToolNames())
                .setSkillDirectories(safeList(skillDirectories))
                .setHooks(toolAccessHooks(policy))
                .setOnPermissionRequest(permissionHandler())
                .setDisabledSkills(safeList(properties.getDisabledSkills()));

        var model = selectedModel(options);
        if (StringUtils.hasText(model)) {
            sessionConfig.setModel(model);
        }

        var reasoningEffort = selectedReasoningEffort(options);
        if (StringUtils.hasText(reasoningEffort)) {
            sessionConfig.setReasoningEffort(reasoningEffort);
        }

        return sessionConfig;
    }

    private String selectedModel(AnalysisAiOptions options) {
        return options != null && StringUtils.hasText(options.model())
                ? options.model()
                : properties.getModel();
    }

    private String selectedReasoningEffort(AnalysisAiOptions options) {
        return options != null && StringUtils.hasText(options.reasoningEffort())
                ? options.reasoningEffort()
                : properties.getReasoningEffort();
    }

    private PermissionHandler permissionHandler() {
        return switch (properties.getPermissionMode()) {
            case DENY_ALL -> (request, invocation) -> CompletableFuture.completedFuture(
                    new PermissionRequestResult().setKind(PermissionRequestResultKind.DENIED_BY_RULES)
            );
            case APPROVE_ALL -> PermissionHandler.APPROVE_ALL;
        };
    }

    private SessionHooks toolAccessHooks(CopilotToolAccessPolicy toolAccessPolicy) {
        return new SessionHooks().setOnPreToolUse((input, invocation) -> {
            var toolName = input != null ? input.getToolName() : null;
            if (toolName != null && toolAccessPolicy.availableToolNames().contains(toolName)) {
                return CompletableFuture.completedFuture(PreToolUseHookOutput.allow());
            }

            return CompletableFuture.completedFuture(PreToolUseHookOutput.deny(
                    "Use only the inline incident artifacts and the explicitly enabled incident-analysis tools for this session."
            ));
        });
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }
}
