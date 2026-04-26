package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequestResult;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import com.github.copilot.sdk.json.PreToolUseHookOutput;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SessionHooks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest;
import pl.mkn.incidenttracker.analysis.ai.copilot.telemetry.CopilotSessionMetricsRegistry;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotSdkToolBridge;
import pl.mkn.incidenttracker.analysis.ai.copilot.tools.CopilotToolSessionContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class CopilotSdkPreparationService {

    private final CopilotSdkProperties properties;
    private final CopilotSdkToolBridge toolBridge;
    private final CopilotSkillRuntimeLoader skillRuntimeLoader;
    private final CopilotArtifactService artifactService;
    private final CopilotToolAccessPolicyFactory toolAccessPolicyFactory;
    private final CopilotPromptRenderer promptRenderer;
    private final CopilotSessionMetricsRegistry metricsRegistry;

    public CopilotSdkPreparedRequest prepare(AnalysisAiAnalysisRequest request) {
        var preparationStart = System.nanoTime();
        var toolSessionContext = buildToolSessionContext(request);
        var registeredTools = toolBridge.buildToolDefinitions(toolSessionContext);
        var toolAccessPolicy = toolAccessPolicyFactory.create(request, registeredTools);
        var tools = toolAccessPolicy.enabledTools();
        var renderedArtifacts = artifactService.renderArtifacts(request, toolAccessPolicy);
        var skillDirectories = skillRuntimeLoader.resolveSkillDirectories();

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

        var sessionConfig = new SessionConfig()
                .setSessionId(toolSessionContext.copilotSessionId())
                .setClientName(properties.getClientName())
                .setWorkingDirectory(properties.getWorkingDirectory())
                .setStreaming(false)
                .setTools(tools)
                .setAvailableTools(toolAccessPolicy.availableToolNames())
                .setSkillDirectories(skillDirectories)
                .setHooks(toolAccessHooks(toolAccessPolicy))
                .setOnPermissionRequest(permissionHandler())
                .setDisabledSkills(safeList(properties.getDisabledSkills()));

        if (properties.getModel() != null && !properties.getModel().isBlank()) {
            sessionConfig.setModel(properties.getModel());
        }

        if (properties.getReasoningEffort() != null && !properties.getReasoningEffort().isBlank()) {
            sessionConfig.setReasoningEffort(properties.getReasoningEffort());
        }

        String prompt = promptRenderer.render(request, toolAccessPolicy, renderedArtifacts);
        var artifactContents = artifactService.toArtifactContentMap(renderedArtifacts);
        var messageOptions = new MessageOptions().setPrompt(prompt);
        metricsRegistry.recordPreparation(
                toolSessionContext,
                request,
                renderedArtifacts,
                prompt,
                (System.nanoTime() - preparationStart) / 1_000_000
        );

        return new CopilotSdkPreparedRequest(
                request.correlationId(),
                clientOptions,
                sessionConfig,
                messageOptions,
                prompt,
                artifactContents,
                request
        );
    }

    private CopilotToolSessionContext buildToolSessionContext(AnalysisAiAnalysisRequest request) {
        var analysisRunId = UUID.randomUUID().toString();
        var copilotSessionId = "analysis-" + analysisRunId;

        return new CopilotToolSessionContext(
                analysisRunId,
                copilotSessionId,
                request.correlationId(),
                request.environment(),
                request.gitLabBranch(),
                request.gitLabGroup()
        );
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

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }
}
