package pl.mkn.tdw.features.uxinspector.job.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

public record UxInspectorJobStartRequest(
        @NotBlank @Size(max = 120) String systemId,
        @NotBlank @Size(max = 160) String branch,
        @NotBlank @Size(max = 240) String viewId,
        @NotBlank @Size(max = 4000) String question,
        @NotBlank String captureId,
        @NotBlank @Size(max = 80) String model,
        @Size(max = 40) String reasoningEffort
) {
    public UxInspectorJobStartRequest {
        systemId = normalize(systemId);
        branch = normalize(branch);
        viewId = normalize(viewId);
        question = normalize(question);
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
        captureId = normalize(captureId);
    }

    public AnalysisAiOptions aiOptions() { return new AnalysisAiOptions(model, reasoningEffort); }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignored) {
        throw new IllegalArgumentException("Unknown UX Inspector request field: " + fieldName);
    }

    private static String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
