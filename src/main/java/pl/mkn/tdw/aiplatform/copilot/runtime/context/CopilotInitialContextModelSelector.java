package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.rpc.SystemMessageConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Explicit, pre-session model selection for consumers that supply an overflow model. */
@Component
@RequiredArgsConstructor
public class CopilotInitialContextModelSelector {

    private final CopilotSdkProperties properties;
    private final CopilotModelOptionsProvider modelOptionsProvider;

    public CopilotRunRequest select(CopilotRunRequest request, CopilotModelSelection overflowModel) {
        properties.validateContextManagementConfiguration();
        if (request.sessionTarget().existing()) {
            throw unavailable("Automatyczny dobór modelu jest dostępny tylko przed utworzeniem nowej sesji.");
        }
        var config = request.sessionConfigRequest();
        var options = modelOptionsProvider.modelOptions(request.auth());
        var models = options != null ? options.models() : List.<CopilotModelOption>of();
        var selection = config.modelSelection();
        var defaultProfile = profile(models, selection);
        var estimate = CopilotInitialContextEstimator.estimate(
                request.prompt(),
                new SystemMessageConfig().setContent(config.durableSystemInstructions()),
                config.tools(),
                properties.getContextTier()
        );
        var selectedProfile = defaultProfile;
        var preference = CopilotContextTierPreference.AUTO;
        var reason = "Pełny materiał początkowy mieści się w zwykłym oknie modelu domyślnego.";
        if (!fits(defaultProfile, estimate, false)) {
            if (!defaultProfile.supportsLongContext()) {
                selection = overflowModel;
                selectedProfile = profile(models, selection);
                reason = "Materiał przekracza zwykłe okno modelu domyślnego bez long_context; wybrano model rozszerzonego kontekstu.";
            } else {
                reason = "Materiał wymaga rozszerzonego okna modelu domyślnego.";
            }
            if (!properties.getContextTier().isEnabled() || !selectedProfile.supportsLongContext()) {
                throw unavailable("Skonfigurowany model nie może zapewnić wymaganego long_context.");
            }
            if (!fits(selectedProfile, estimate, true)) {
                throw unavailable("Pełny materiał początkowy z rezerwą przekracza również rozszerzone okno modelu `"
                        + selectedProfile.id() + "`.");
            }
            preference = CopilotContextTierPreference.LONG_CONTEXT_REQUIRED;
        }

        var details = new LinkedHashMap<String, Object>();
        details.put("phase", "INITIAL_MODEL_SELECTION");
        details.put("defaultModel", defaultProfile.id());
        details.put("selectedModel", selectedProfile.id());
        details.put("reasoningEffort", selection.reasoningEffort() != null ? selection.reasoningEffort() : "");
        details.put("estimatedPromptTokens", estimate.promptTokens());
        details.put("reservedTokens", estimate.reservedTokens());
        details.put("estimatedInitialTokens", estimate.totalTokens());
        details.put("defaultWindowTokens", defaultProfile.defaultContextWindowTokens());
        details.put("selectedWindowTokens", preference == CopilotContextTierPreference.LONG_CONTEXT_REQUIRED
                ? selectedProfile.longContextWindowTokens() : selectedProfile.defaultContextWindowTokens());
        details.put("contextTierPreference", preference.name());
        request.activitySink().accept(new AnalysisAiActivityEvent(
                UUID.randomUUID().toString(), null, "platform.model_selection", "CONTEXT", "COMPLETED",
                "Dobór modelu do kontekstu", reason, null, null, null, null, Instant.now(), details
        ));
        return request.withSessionConfigRequest(config.withModelSelection(selection).withContextTierPreference(preference));
    }

    private CopilotModelOption profile(List<CopilotModelOption> models, CopilotModelSelection selection) {
        if (selection == null || selection.model() == null) {
            throw unavailable("Konfiguracja modelu assessmentu jest niekompletna.");
        }
        var profile = models.stream()
                .filter(model -> model.id().equalsIgnoreCase(selection.model()))
                .findFirst()
                .orElseThrow(() -> unavailable("Katalog Copilota nie potwierdza modelu `" + selection.model() + "`."));
        if (profile.defaultContextWindowTokens() <= 0) {
            throw unavailable("Brak wiarygodnego limitu kontekstu modelu `" + profile.id() + "`.");
        }
        if (selection.reasoningEffort() != null && (!profile.supportsReasoningEffort()
                || !profile.reasoningEfforts().isEmpty() && !profile.reasoningEfforts().contains(selection.reasoningEffort()))) {
            throw unavailable("Model `" + profile.id() + "` nie obsługuje skonfigurowanego reasoning effort `"
                    + selection.reasoningEffort() + "`.");
        }
        return profile;
    }

    private boolean fits(CopilotModelOption model, CopilotInitialContextEstimate estimate, boolean extended) {
        var window = extended ? model.longContextWindowTokens() : model.defaultContextWindowTokens();
        var promptLimit = extended ? model.longContextPromptTokens() : model.defaultPromptTokens();
        return window > estimate.totalTokens() && (promptLimit <= 0 || promptLimit > estimate.promptTokens());
    }

    private CopilotRequiredContextTierException unavailable(String message) {
        return new CopilotRequiredContextTierException(message);
    }
}
