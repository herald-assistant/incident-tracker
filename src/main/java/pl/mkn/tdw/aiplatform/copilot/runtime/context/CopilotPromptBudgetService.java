package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;

@Component
@RequiredArgsConstructor
public class CopilotPromptBudgetService {
    private final CopilotSdkProperties properties;
    private final CopilotModelOptionsProvider modelOptionsProvider;

    public CopilotPromptBudget measure(CopilotPreparedSession session) {
        properties.validateContextManagementConfiguration();
        var estimate = CopilotInitialContextEstimator.estimate(session, properties.getContextTier());
        var model = session.sessionTarget().existing() && session.resumeSessionConfig() != null
                ? session.resumeSessionConfig().getModel()
                : session.sessionConfig() != null ? session.sessionConfig().getModel() : null;
        if (model == null || model.isBlank()) model = properties.getModel();
        final var selected = model;
        var response = modelOptionsProvider.modelOptions(session.auth());
        var profile = response != null ? response.models().stream()
                .filter(option -> selected != null && option.id().equalsIgnoreCase(selected.trim())).findFirst().orElse(null) : null;
        var extended = profile != null && properties.getContextTier().isEnabled() && profile.supportsLongContext();
        return new CopilotPromptBudget(estimate,
                profile == null ? 0 : extended ? profile.longPromptTokens() : profile.defaultPromptTokens(),
                profile != null ? profile.defaultPromptTokens() : 0,
                profile != null ? profile.maxOutputTokens() : 0,
                properties.getContextTier().getReservedTokens(), properties.getPromptBudgetSafetyRatio(), extended && estimate > profile.defaultPromptTokens() * properties.getPromptBudgetSafetyRatio());
    }
}
