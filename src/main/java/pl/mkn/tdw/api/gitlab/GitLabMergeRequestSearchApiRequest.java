package pl.mkn.tdw.api.gitlab;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GitLabMergeRequestSearchApiRequest(
        @Size(max = 500, message = "group must contain at most 500 characters")
        String group,
        @NotBlank(message = "issueKey must not be blank")
        @Size(max = 80, message = "issueKey must contain at most 80 characters")
        String issueKey,
        @Min(value = 1, message = "maxResults must be at least 1")
        @Max(value = 100, message = "maxResults must be at most 100")
        Integer maxResults
) {
}
