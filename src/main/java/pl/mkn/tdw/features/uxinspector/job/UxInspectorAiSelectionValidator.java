package pl.mkn.tdw.features.uxinspector.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

@Component
@RequiredArgsConstructor
public class UxInspectorAiSelectionValidator {
    private final CopilotModelOptionsProvider modelOptionsProvider;
    private final CopilotRunAuthMapper runAuthMapper;

    public void validate(String model, String reasoningEffort, AnalysisAiAuthRef authRef) {
        if (!StringUtils.hasText(model)) {
            throw invalid("Select an AI model before starting UX Inspector.");
        }
        var options = modelOptionsProvider.modelOptions(runAuthMapper.toRunAuth(authRef));
        if (options == null || options.models().isEmpty()) {
            throw new UxInspectorJobException("UX_INSPECTOR_AI_OPTIONS_UNAVAILABLE",
                    UserFacingErrorType.SERVICE_UNAVAILABLE,
                    "AI model options are unavailable. Reload the options before starting UX Inspector.");
        }
        var selected = options.models().stream().filter(value -> model.trim().equals(value.id())).findFirst()
                .orElseThrow(() -> invalid("Selected AI model is not available."));
        if (!selected.supportsReasoningEffort()) {
            if (StringUtils.hasText(reasoningEffort)) {
                throw invalid("Selected AI model does not accept a reasoning effort.");
            }
            return;
        }
        if (!StringUtils.hasText(reasoningEffort)
                || !selected.reasoningEfforts().contains(reasoningEffort.trim())) {
            throw invalid("Select a reasoning effort supported by the chosen AI model.");
        }
    }

    private UxInspectorJobException invalid(String message) {
        return new UxInspectorJobException("UX_INSPECTOR_AI_SELECTION_INVALID",
                UserFacingErrorType.BAD_REQUEST, message);
    }
}
