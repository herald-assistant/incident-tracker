package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequestResult;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import com.github.copilot.sdk.json.PreToolUseHookOutput;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SessionHooks;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CopilotSessionConfigFactory {

    private final CopilotSdkProperties properties;
    private final CopilotAccessTokenResolver accessTokenResolver;

    public CopilotSessionConfigFactory(CopilotSdkProperties properties) {
        this(
                properties,
                auth -> new pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotAccessToken(
                        testCompatibleToken(properties),
                        null,
                        null,
                        false
                )
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

    public CopilotClientOptions clientOptions(CopilotRunAuth auth) {
        var accessToken = accessTokenResolver.resolve(auth);
        var clientOptions = new CopilotClientOptions()
                .setUseLoggedInUser(false)
                .setGitHubToken(accessToken.value())
                .setCwd(properties.getWorkingDirectory());

        if (properties.getCliPath() != null && !properties.getCliPath().isBlank()) {
            clientOptions.setCliPath(properties.getCliPath());
        }

        return clientOptions;
    }

    public CopilotClientOptions clientOptions() {
        return clientOptions(CopilotRunAuth.localToken());
    }

    public SessionConfig sessionConfig(CopilotSessionConfigRequest request) {
        var sessionConfig = new SessionConfig()
                .setSessionId(request.sessionId())
                .setClientName(properties.getClientName())
                .setWorkingDirectory(properties.getWorkingDirectory())
                .setStreaming(false)
                .setTools(request.tools())
                .setAvailableTools(request.availableToolNames())
                .setSkillDirectories(request.skillDirectories())
                .setHooks(toolAccessHooks(request.availableToolNames(), request.deniedToolUseMessage()))
                .setOnPermissionRequest(permissionHandler())
                .setDisabledSkills(safeList(properties.getDisabledSkills()));

        var model = selectedModel(request.modelSelection());
        if (StringUtils.hasText(model)) {
            sessionConfig.setModel(model);
        }

        var reasoningEffort = selectedReasoningEffort(request.modelSelection());
        if (StringUtils.hasText(reasoningEffort)) {
            sessionConfig.setReasoningEffort(reasoningEffort);
        }

        return sessionConfig;
    }

    private String selectedModel(CopilotModelSelection modelSelection) {
        return modelSelection != null && StringUtils.hasText(modelSelection.model())
                ? modelSelection.model()
                : properties.getModel();
    }

    private String selectedReasoningEffort(CopilotModelSelection modelSelection) {
        return modelSelection != null && StringUtils.hasText(modelSelection.reasoningEffort())
                ? modelSelection.reasoningEffort()
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
