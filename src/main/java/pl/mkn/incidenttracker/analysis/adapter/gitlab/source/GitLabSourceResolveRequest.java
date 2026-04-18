package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

import jakarta.validation.constraints.NotBlank;
import org.springframework.util.StringUtils;

public record GitLabSourceResolveRequest(
        @NotBlank(message = "gitlabBaseUrl must not be blank") String gitlabBaseUrl,
        @NotBlank(message = "groupPath must not be blank") String groupPath,
        @NotBlank(message = "projectPath must not be blank") String projectPath,
        String ref,
        @NotBlank(message = "symbol must not be blank") String symbol
) {

    public String effectiveRef() {
        return StringUtils.hasText(ref) ? ref.trim() : "HEAD";
    }

}
