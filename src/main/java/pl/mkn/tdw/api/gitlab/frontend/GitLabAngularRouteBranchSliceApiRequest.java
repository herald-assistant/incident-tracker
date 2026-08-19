package pl.mkn.tdw.api.gitlab.frontend;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;

import java.util.List;

public record GitLabAngularRouteBranchSliceApiRequest(
        @NotBlank @Size(max = 240) String group,
        @NotBlank @Size(max = 240) String projectName,
        @NotBlank @Size(max = 255) String ref,
        @Size(max = 20) List<
                @NotBlank
                @Size(max = 500)
                @Pattern(regexp = "^(?!.*\\.\\.)(?!.*//).*$") String> pathPrefixes,
        @NotBlank @Size(max = 160) String screenId,
        @Size(max = 160) String expectedRevision,
        Boolean includeDescendantRoutes,
        @Min(1000) @Max(80000) Integer maxCharacters
) {

    GitLabAngularRouteBranchSliceRequest toIntegrationRequest() {
        return new GitLabAngularRouteBranchSliceRequest(
                new GitLabFrontendRepositoryScope(
                        group, projectName, ref, pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of()
                ),
                screenId,
                expectedRevision,
                includeDescendantRoutes,
                maxCharacters
        );
    }
}
