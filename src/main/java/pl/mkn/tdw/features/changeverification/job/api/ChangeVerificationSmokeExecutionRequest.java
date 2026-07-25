package pl.mkn.tdw.features.changeverification.job.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record ChangeVerificationSmokeExecutionRequest(
        @NotBlank(message = "baseUrl must be provided")
        @Size(max = 1000, message = "baseUrl must not exceed 1000 characters")
        String baseUrl,
        @Size(max = 80, message = "environment must not exceed 80 characters")
        String environment,
        @Size(max = 120, message = "databaseApplication must not exceed 120 characters")
        String databaseApplication,
        List<String> selectedTestIds,
        Map<String, String> variables,
        Boolean executeCleanup
) {

    public ChangeVerificationSmokeExecutionRequest {
        environment = normalize(environment);
        databaseApplication = normalize(databaseApplication);
        selectedTestIds = selectedTestIds != null ? List.copyOf(selectedTestIds) : List.of();
        variables = variables != null ? Map.copyOf(variables) : Map.of();
        executeCleanup = executeCleanup != null ? executeCleanup : false;
    }

    private static String normalize(String value) {
        return org.springframework.util.StringUtils.hasText(value) ? value.trim() : null;
    }
}
