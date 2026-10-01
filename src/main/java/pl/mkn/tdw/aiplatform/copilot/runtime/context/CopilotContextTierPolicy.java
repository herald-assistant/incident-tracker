package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;


@Component
@RequiredArgsConstructor
public class CopilotContextTierPolicy {

    static final String LONG_CONTEXT = "long_context";

    private final CopilotSdkProperties properties;
    private final CopilotModelOptionsProvider modelOptionsProvider;
    private final CopilotEffectiveContextTierReader effectiveContextTierReader;
    private final CopilotContextTierActivator contextTierActivator;

    public CopilotContextTierSession prepare(CopilotPreparedSession preparedSession) {
        return prepare(preparedSession, null);
    }

    public CopilotContextTierSession prepare(CopilotPreparedSession preparedSession, CopilotPromptBudget budget) {
        var decision = decide(preparedSession);
        if (budget != null && budget.known() && decision.preference() == CopilotContextTierPreference.AUTO) {
            decision = new CopilotContextTierDecision(decision.policyEnabled(), decision.preference(),
                    decision.modelMetadataAvailable(), decision.modelId(), decision.defaultWindowTokens(),
                    decision.longContextWindowTokens(), budget.estimatedInputTokens(), decision.initialThresholdTokens(),
                    decision.runtimeUsageThreshold(), decision.switchTimeoutMillis(), budget.requiresLongContext(),
                    "The prepared input uses the measured prompt budget and requires only its selected tier.");
        }
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
                effectiveContextTierReader,
                contextTierActivator
        );
    }

    CopilotContextTierDecision decide(CopilotPreparedSession preparedSession) {
        var settings = properties.getContextTier();
        properties.validateContextManagementConfiguration();
        var modelId = selectedModel(preparedSession);
        var preference = preparedSession.contextTierPreference() != null
                ? preparedSession.contextTierPreference()
                : CopilotContextTierPreference.AUTO;
        if (!settings.isEnabled()) {
            if (preference == CopilotContextTierPreference.LONG_CONTEXT_REQUIRED) {
                throw new CopilotRequiredContextTierException(
                        "Asysta wymaga `long_context`, ale polityka platformowa "
                                + "analysis.ai.copilot.context-tier.enabled jest wyłączona."
                );
            }
            return unsupported(false, preference, modelId, "Platform context-tier policy is disabled.");
        }

        var estimatedTokens = CopilotInitialContextEstimator.estimate(preparedSession, settings);
        if (preference == CopilotContextTierPreference.LONG_CONTEXT_REQUIRED) {
            var profile = findAnyProfileBestEffort(preparedSession, modelId);
            if (profile != null && !profile.supportsLongContext()) {
                throw new CopilotRequiredContextTierException(
                        "Wybrany model Copilota `" + profile.id() + "` nie obsługuje wymaganego `long_context`. "
                                + "Wybierz model obsługujący rozszerzony kontekst."
                );
            }
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

    private CopilotModelOption findAnyProfileBestEffort(CopilotPreparedSession preparedSession, String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        try {
            var response = modelOptionsProvider.modelOptions(preparedSession.auth());
            return response != null ? response.models().stream()
                    .filter(profile -> profile.id().equalsIgnoreCase(modelId.trim()))
                    .findFirst()
                    .orElse(null) : null;
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

}
