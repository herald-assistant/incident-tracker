package pl.mkn.incidenttracker.analysis.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiOptions;
import pl.mkn.incidenttracker.analysis.flow.AnalysisRequest;

public record AnalysisJobStartRequest(
        @NotBlank(message = "correlationId must not be blank") String correlationId,
        @Size(max = 80, message = "model must not exceed 80 characters") String model,
        @Pattern(
                regexp = "low|medium|high|xhigh",
                message = "reasoningEffort must be one of: low, medium, high, xhigh"
        )
        String reasoningEffort
) {

    public AnalysisJobStartRequest {
        correlationId = StringUtils.hasText(correlationId) ? correlationId.trim() : correlationId;
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
    }

    public static AnalysisJobStartRequest from(AnalysisRequest request) {
        return new AnalysisJobStartRequest(request.correlationId(), null, null);
    }

    public AnalysisAiOptions aiOptions() {
        return new AnalysisAiOptions(model, reasoningEffort);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
