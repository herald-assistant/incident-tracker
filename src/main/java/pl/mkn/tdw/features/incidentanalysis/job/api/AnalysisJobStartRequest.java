package pl.mkn.tdw.features.incidentanalysis.job.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

public record AnalysisJobStartRequest(
        @NotBlank(message = "correlationId must not be blank") String correlationId,
        @Size(max = 80, message = "model must not exceed 80 characters") String model,
        @Size(max = 40, message = "reasoningEffort must not exceed 40 characters")
        String reasoningEffort
) {

    public AnalysisJobStartRequest {
        correlationId = StringUtils.hasText(correlationId) ? correlationId.trim() : correlationId;
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
    }

    public AnalysisAiOptions aiOptions() {
        return new AnalysisAiOptions(model, reasoningEffort);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
