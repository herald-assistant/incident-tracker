package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.rpc.SystemMessageConfig;
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
    private final CopilotEffectiveContextTierReader effectiveContextTierReader;

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
                decision,
                preparedSession.activitySink(),
                effectiveContextTierReader
        );
    }

    CopilotContextTierDecision decide(CopilotPreparedSession preparedSession) {
        var settings = properties.getContextTier();
        validate(settings);
        var modelId = selectedModel(preparedSession);
        var preference = preparedSession.contextTierPreference() != null
                ? preparedSession.contextTierPreference()
                : CopilotContextTierPreference.AUTO;
        if (!settings.isEnabled()) {
            return unsupported(false, preference, modelId, "Platform context-tier policy is disabled.");
        }

        var estimatedTokens = estimateInitialTokens(preparedSession, settings);
        if (preference == CopilotContextTierPreference.LONG_CONTEXT_REQUIRED) {
            var profile = findProfileBestEffort(preparedSession, modelId);
            return new CopilotContextTierDecision(
                    true,
                    preference,
                    profile != null,
                    profile != null ? profile.id() : modelId,
                    profile != null ? profile.defaultContextWindowTokens() : 0,
                    profile != null ? profile.longContextWindowTokens() : 0,
                    estimatedTokens,
                    0,
                    settings.getRuntimeUsageThreshold(),
                    settings.getVerificationTimeout().toMillis(),
                    true,
                    "The feature requires long_context before its first message; SDK state will be verified after session open."
            );
        }

        var profile = findProfile(preparedSession, modelId);
        if (profile == null) {
            return unsupported(
                    true,
                    preference,
                    modelId,
                    "Dynamic model catalog does not expose a long-context tier for the selected model."
            );
        }

        var thresholdTokens = Math.round(profile.defaultContextWindowTokens() * settings.getInitialPromptThreshold());
        return new CopilotContextTierDecision(
                true,
                preference,
                true,
                profile.id(),
                profile.defaultContextWindowTokens(),
                profile.longContextWindowTokens(),
                estimatedTokens,
                thresholdTokens,
                settings.getRuntimeUsageThreshold(),
                settings.getVerificationTimeout().toMillis(),
                estimatedTokens >= thresholdTokens,
                estimatedTokens >= thresholdTokens
                        ? "Estimated initial context reached the configured default-window threshold."
                        : "Estimated initial context remains below the configured default-window threshold."
        );
    }

    private CopilotContextTierDecision unsupported(
            boolean enabled,
            CopilotContextTierPreference preference,
            String modelId,
            String reason
    ) {
        return new CopilotContextTierDecision(
                enabled,
                preference,
                false,
                modelId,
                0,
                0,
                0,
                0,
                properties.getContextTier().getRuntimeUsageThreshold(),
                properties.getContextTier().getVerificationTimeout().toMillis(),
                false,
                reason
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
        characters += systemMessageCharacters(selectedSystemMessage(preparedSession));
        return (long) Math.ceil(characters / settings.getEstimatedCharactersPerToken())
                + settings.getReservedTokens();
    }

    private SystemMessageConfig selectedSystemMessage(CopilotPreparedSession preparedSession) {
        if (preparedSession.sessionTarget() != null && preparedSession.sessionTarget().existing()) {
            return preparedSession.resumeSessionConfig() != null
                    ? preparedSession.resumeSessionConfig().getSystemMessage()
                    : null;
        }
        return preparedSession.sessionConfig() != null
                ? preparedSession.sessionConfig().getSystemMessage()
                : null;
    }

    private long systemMessageCharacters(SystemMessageConfig systemMessage) {
        if (systemMessage == null) {
            return 0L;
        }
        long characters = length(systemMessage.getContent());
        if (systemMessage.getSections() != null) {
            for (var entry : systemMessage.getSections().entrySet()) {
                characters += length(entry.getKey());
                if (entry.getValue() != null) {
                    characters += length(entry.getValue().getAction());
                    characters += length(entry.getValue().getContent());
                }
            }
        }
        return characters;
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

    private CopilotModelOption findProfileBestEffort(CopilotPreparedSession preparedSession, String modelId) {
        try {
            return findProfile(preparedSession, modelId);
        } catch (RuntimeException ignored) {
            return null;
        }
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
        if (settings.getRuntimeUsageThreshold() <= 0D || settings.getRuntimeUsageThreshold() >= 1D) {
            throw new IllegalStateException("analysis.ai.copilot.context-tier.runtime-usage-threshold must be in (0, 1)");
        }
        if (settings.getEstimatedCharactersPerToken() <= 0D) {
            throw new IllegalStateException("analysis.ai.copilot.context-tier.estimated-characters-per-token must be positive");
        }
        if (settings.getReservedTokens() < 0) {
            throw new IllegalStateException("analysis.ai.copilot.context-tier.reserved-tokens must not be negative");
        }
        if (settings.getVerificationTimeout() == null
                || settings.getVerificationTimeout().isZero()
                || settings.getVerificationTimeout().isNegative()) {
            throw new IllegalStateException("analysis.ai.copilot.context-tier.verification-timeout must be positive");
        }
    }

}
