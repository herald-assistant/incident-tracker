package pl.mkn.tdw.api.gitlab.frontend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendGraphLimits;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;

import java.util.List;

public record GitLabFrontendCatalogApiRequest(
        @NotBlank @Size(max = 240) String group,
        @NotBlank @Size(max = 240) String projectName,
        @NotBlank @Size(max = 255) String ref,
        @Size(max = 20) List<
                @NotBlank
                @Size(max = 500)
                @Pattern(regexp = "^(?!.*\\.\\.)(?!.*//).*$") String> pathPrefixes
) {

    GitLabFrontendRepositoryScope toScope() {
        return new GitLabFrontendRepositoryScope(group, projectName, ref, normalizedPathPrefixes());
    }

    GitLabFrontendGraphLimits limits() {
        return GitLabFrontendGraphLimits.defaults();
    }

    private List<String> normalizedPathPrefixes() {
        return pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
    }
}
