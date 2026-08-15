package pl.mkn.tdw.api.gitlab.frontend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryLimits;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenContextRequest;

import java.util.List;

public record GitLabFrontendScreenContextApiRequest(
        @NotBlank @Size(max = 240) String group,
        @NotBlank @Size(max = 240) String projectName,
        @NotBlank @Size(max = 255) String ref,
        @Size(max = 20) List<
                @NotBlank
                @Size(max = 500)
                @Pattern(regexp = "^(?!.*\\.\\.)(?!.*//).*$") String> pathPrefixes,
        @NotBlank @Size(max = 160) String screenId
) {

    GitLabFrontendScreenContextRequest toIntegrationRequest() {
        return new GitLabFrontendScreenContextRequest(
                new GitLabFrontendRepositoryScope(group, projectName, ref, normalizedPathPrefixes()),
                screenId,
                GitLabFrontendDiscoveryLimits.defaults()
        );
    }

    private List<String> normalizedPathPrefixes() {
        return pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
    }
}
