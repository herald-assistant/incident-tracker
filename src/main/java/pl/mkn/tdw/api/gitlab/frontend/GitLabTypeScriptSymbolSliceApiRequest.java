package pl.mkn.tdw.api.gitlab.frontend;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceRequest;

import java.util.List;

public record GitLabTypeScriptSymbolSliceApiRequest(
        @NotBlank @Size(max = 240) String group,
        @NotBlank @Size(max = 240) String projectName,
        @NotBlank @Size(max = 255) String ref,
        @Size(max = 20) List<
                @NotBlank
                @Size(max = 500)
                @Pattern(regexp = "^(?!.*\\.\\.)(?!.*//).*$") String> pathPrefixes,
        @NotBlank
        @Size(max = 700)
        @Pattern(regexp = "^(?!/)(?!.*\\.\\.)(?!.*//).+\\.tsx?$") String filePath,
        @Size(max = 300) String declaringTypeName,
        @NotEmpty @Size(max = 30) List<@Valid GitLabTypeScriptSymbolSelectorApiRequest> symbolSelectors,
        Boolean includeLocalHelpers,
        Boolean includeRelevantFields,
        Boolean includeRelevantImports,
        @Min(1000) @Max(40000) Integer maxCharacters
) {

    GitLabTypeScriptSymbolSliceRequest toIntegrationRequest() {
        return new GitLabTypeScriptSymbolSliceRequest(
                new GitLabFrontendRepositoryScope(
                        group, projectName, ref, pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of()
                ),
                filePath,
                declaringTypeName,
                symbolSelectors.stream().map(GitLabTypeScriptSymbolSelectorApiRequest::toIntegrationSelector).toList(),
                includeLocalHelpers,
                includeRelevantFields,
                includeRelevantImports,
                maxCharacters
        );
    }
}
