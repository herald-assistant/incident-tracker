package pl.mkn.incidenttracker.api.gitlab;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GitLabRepositoryFilesByPathApiRequest(
        @NotBlank(message = "group must not be blank")
        String group,
        @NotBlank(message = "projectName must not be blank")
        String projectName,
        @NotBlank(message = "branch must not be blank")
        String branch,
        @NotEmpty(message = "filePaths must not be empty")
        @Size(max = GitLabRepositoryFilesByPathApiService.MAX_FILE_COUNT, message = "filePaths must contain at most 100 entries")
        List<@NotBlank(message = "file path must not be blank") @Size(max = 700, message = "file path must contain at most 700 characters") String> filePaths,
        @Min(value = 1, message = "maxCharactersPerFile must be at least 1")
        @Max(value = GitLabRepositoryFilesByPathApiService.MAX_CHARACTERS_PER_FILE, message = "maxCharactersPerFile must be at most 120000")
        Integer maxCharactersPerFile,
        @Min(value = 1, message = "maxTotalCharacters must be at least 1")
        @Max(value = GitLabRepositoryFilesByPathApiService.MAX_TOTAL_CHARACTERS, message = "maxTotalCharacters must be at most 500000")
        Integer maxTotalCharacters
) {
}
