package pl.mkn.tdw.features.deliveryscopecomplexity.job.api;

import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record DeliveryScopeComplexityJobStartRequest(
        @NotBlank(message = "jiraProject is required")
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,49}", message = "jiraProject has invalid format")
        String jiraProject,
        @NotNull(message = "fromDate is required")
        LocalDate fromDate,
        @NotNull(message = "toDate is required")
        LocalDate toDate
) {

    public DeliveryScopeComplexityJobStartRequest {
        jiraProject = normalize(jiraProject);
    }

    @AssertTrue(message = "fromDate must not be after toDate")
    public boolean isDateRangeOrdered() {
        return fromDate == null || toDate == null || !fromDate.isAfter(toDate);
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported assessment start field: " + name);
    }

    private static String normalize(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
