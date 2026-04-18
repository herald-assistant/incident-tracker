package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

import java.util.List;

public record GitLabRepositorySearchRequest(
        String correlationId,
        String branch,
        @NotNull(message = "projectHints must not be null")
        @NotEmpty(message = "projectHints must not be empty")
        @Size(max = 20, message = "projectHints must contain at most 20 items")
        List<String> projectHints,
        @Size(max = 20, message = "operationNames must contain at most 20 items")
        List<String> operationNames,
        @Size(max = 20, message = "keywords must contain at most 20 items")
        List<String> keywords
) {

    public String effectiveBranch() {
        return StringUtils.hasText(branch) ? branch.trim() : "HEAD";
    }

}
