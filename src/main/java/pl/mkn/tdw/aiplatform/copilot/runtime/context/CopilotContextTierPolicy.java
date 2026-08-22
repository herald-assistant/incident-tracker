package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.rpc.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CopilotContextTierPolicy {

    static final String LONG_CONTEXT = "long_context";

    private final CopilotSdkProperties properties;
    private final CopilotModelOptionsProvider modelOptionsProvider;

    public CopilotContextTierSession prepare(CopilotPreparedSession preparedSession) {
        var decision = decide(preparedSession);
        if (decision.useLongContextInitially()) {
            if (preparedSession.sessionConfig() != null) {
                preparedSession.sessionConfig().setContextTier(LONG_CONTEXT);
            }
            if (preparedSession.resumeSessionConfig() != null) {
                preparedSession.resumeSessionConfig().setContextTier(LONG_CONTEXT);
            }
        }
        return new CopilotContextTierSession(
                properties.getContextTier(),
                decision,
                selectedReasoningEffort(preparedSession),
                preparedSession.activitySink()
        );
    }

    CopilotContextTierDecision decide(CopilotPreparedSession preparedSession) {
        var settings = properties.getContextTier();
        validate(settings);
        var modelId = selectedModel(preparedSession);
        if (!settings.isEnabled()) {
            return unsupported(false, modelId, "Platform context-tier policy is disabled.");
        }

        var profile = findProfile(preparedSession, modelId);
        if (profile == null) {
            return unsupported(true, modelId, "Dynamic model catalog does not expose a long-context tier for the selected model.");
        }

        var estimatedTokens = estimateInitialTokens(preparedSession, settings);
        var thresholdTokens = Math.round(profile.defaultContextWindowTokens() * settings.getInitialPromptThreshold());
        return new CopilotContextTierDecision(
                true,
                true,
                profile.id(),
                profile.defaultContextWindowTokens(),
                profile.longContextWindowTokens(),
                estimatedTokens,
                thresholdTokens,
                estimatedTokens >= thresholdTokens,
                estimatedTokens >= thresholdTokens
                        ? "Estimated initial context reached the configured default-window threshold."
                        : "Estimated initial context remains below the configured default-window threshold."
        );
    }

    private CopilotContextTierDecision unsupported(boolean enabled, String modelId, String reason) {
        return new CopilotContextTierDecision(
                enabled, false, modelId, 0, 0, 0, 0, false, reason
        );
    }

    private long estimateInitialTokens(
            CopilotPreparedSession preparedSession,
            CopilotSdkProperties.ContextTierPolicy settings
    ) {
        long characters = preparedSession.prompt() != null ? preparedSession.prompt().length() : 0L;
        var tools = preparedSession.sessionConfig() != null
                ? preparedSession.sessionConfig().getTools()
                : List.<ToolDefinition>of();
        if (tools != null) {
            for (var tool : tools) {
                if (tool == null) {
                    continue;
                }
                characters += length(tool.name());
                characters += length(tool.description());
                characters += length(tool.parameters());
            }
        }
        return (long) Math.ceil(characters / settings.getEstimatedCharactersPerToken())
                + settings.getReservedTokens();
    }

    private CopilotModelOption findProfile(CopilotPreparedSession preparedSession, String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        var response = modelOptionsProvider.modelOptions(preparedSession.auth());
        if (response == null) {
            return null;
        }
        return response.models().stream()
                .filter(CopilotModelOption::supportsLongContext)
                .filter(profile -> profile.id().equalsIgnoreCase(modelId.trim()))
                .findFirst()
                .orElse(null);
    }

    private String selectedModel(CopilotPreparedSession preparedSession) {
        if (preparedSession.sessionTarget() != null && preparedSession.sessionTarget().existing()) {
            if (preparedSession.resumeSessionConfig() != null
                    && StringUtils.hasText(preparedSession.resumeSessionConfig().getModel())) {
                return preparedSession.resumeSessionConfig().getModel().trim();
            }
        }
        if (preparedSession.sessionConfig() != null && StringUtils.hasText(preparedSession.sessionConfig().getModel())) {
            return preparedSession.sessionConfig().getModel().trim();
        }
        return StringUtils.hasText(properties.getModel()) ? properties.getModel().trim() : null;
    }

    private String selectedReasoningEffort(CopilotPreparedSession preparedSession) {
        if (preparedSession.sessionTarget() != null && preparedSession.sessionTarget().existing()) {
            if (preparedSession.resumeSessionConfig() != null
                    && StringUtils.hasText(preparedSession.resumeSessionConfig().getReasoningEffort())) {
                return preparedSession.resumeSessionConfig().getReasoningEffort().trim();
            }
        }
        if (preparedSession.sessionConfig() != null
                && StringUtils.hasText(preparedSession.sessionConfig().getReasoningEffort())) {
            return preparedSession.sessionConfig().getReasoningEffort().trim();
        }
        return null;
    }

    private int length(Object value) {
        return value != null ? String.valueOf(value).length() : 0;
    }

    private void validate(CopilotSdkProperties.ContextTierPolicy settings) {
        if (settings == null) {
            throw new IllegalStateException("analysis.ai.copilot.context-tier must be configured");
        }
        if (settings.getInitialPromptThreshold() <= 0D || settings.getInitialPromptThreshold() > 1D) {
            throw new IllegalStateException("analysis.ai.copilot.context-tier.initial-prompt-threshold must be in (0, 1]");
        }
        if (settings.getRuntimeUsageThreshold() <= 0D || settings.getRuntimeUsageThreshold() > 1D) {
            throw new IllegalStateException("analysis.ai.copilot.context-tier.runtime-usage-threshold must be in (0, 1]");
        }
        if (settings.getEstimatedCharactersPerToken() <= 0D) {
            throw new IllegalStateException("analysis.ai.copilot.context-tier.estimated-characters-per-token must be positive");
        }
        if (settings.getReservedTokens() < 0) {
            throw new IllegalStateException("analysis.ai.copilot.context-tier.reserved-tokens must not be negative");
        }
    }

}
