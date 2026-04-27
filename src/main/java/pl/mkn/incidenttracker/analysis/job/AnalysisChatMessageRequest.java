package pl.mkn.incidenttracker.analysis.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

public record AnalysisChatMessageRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max = 8000, message = "message must not exceed 8000 characters")
        String message
) {

    public AnalysisChatMessageRequest {
        message = StringUtils.hasText(message) ? message.trim() : message;
    }
}
