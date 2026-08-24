package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.time.LocalDate;

public record DeliveryScopeComplexityJobStartRequest(
        @NotBlank(message = "jiraProject is required")
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,49}", message = "jiraProject has invalid format")
        String jiraProject,
        @NotNull(message = "fromDate is required")
        LocalDate fromDate,
        @NotNull(message = "toDate is required")
        LocalDate toDate,
        @Size(max = 80, message = "model must not exceed 80 characters")
        String model,
        @Size(max = 40, message = "reasoningEffort must not exceed 40 characters")
        String reasoningEffort
) {

    public DeliveryScopeComplexityJobStartRequest {
        jiraProject = normalize(jiraProject);
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
    }

    @AssertTrue(message = "fromDate must not be after toDate")
    public boolean isDateRangeOrdered() {
        return fromDate == null || toDate == null || !fromDate.isAfter(toDate);
    }

    public AnalysisAiOptions aiOptions() {
        return new AnalysisAiOptions(model, reasoningEffort);
    }

    private static String normalize(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
