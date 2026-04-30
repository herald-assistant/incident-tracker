package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequestResult;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import com.github.copilot.sdk.json.PreToolUseHookOutput;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SessionHooks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.options.AnalysisAiOptions;

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

    public SessionConfig sessionConfig(CopilotSessionConfigRequest request) {
        var sessionConfig = new SessionConfig()
                .setSessionId(request.context().copilotSessionId())
                .setClientName(properties.getClientName())
                .setWorkingDirectory(properties.getWorkingDirectory())
                .setStreaming(false)
                .setTools(request.tools())
                .setAvailableTools(request.availableToolNames())
                .setSkillDirectories(request.skillDirectories())
                .setHooks(toolAccessHooks(request.availableToolNames(), request.deniedToolUseMessage()))
                .setOnPermissionRequest(permissionHandler())
                .setDisabledSkills(safeList(properties.getDisabledSkills()));

        var model = selectedModel(request.options());
        if (StringUtils.hasText(model)) {
            sessionConfig.setModel(model);
        }

        var reasoningEffort = selectedReasoningEffort(request.options());
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

    private SessionHooks toolAccessHooks(List<String> availableToolNames, String deniedToolUseMessage) {
        var allowedToolNames = safeList(availableToolNames);
        return new SessionHooks().setOnPreToolUse((input, invocation) -> {
            var toolName = input != null ? input.getToolName() : null;
            if (toolName != null && allowedToolNames.contains(toolName)) {
                return CompletableFuture.completedFuture(PreToolUseHookOutput.allow());
            }

            return CompletableFuture.completedFuture(PreToolUseHookOutput.deny(deniedToolUseMessage));
        });
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }
}
