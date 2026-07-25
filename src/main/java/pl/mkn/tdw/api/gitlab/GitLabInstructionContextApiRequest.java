package pl.mkn.tdw.api.gitlab;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextRequest;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionRepositoryScope;

import java.util.List;

public record GitLabInstructionContextApiRequest(
        @NotBlank(message = "repositoryKey must not be blank")
        @Size(max = 500, message = "repositoryKey must contain at most 500 characters")
        String repositoryKey,
        @NotBlank(message = "ref must not be blank")
        @Size(max = 200, message = "ref must contain at most 200 characters")
        String ref,
        @Size(max = 250, message = "changedFilePaths must contain at most 250 items")
        List<@Size(max = 1000, message = "changedFilePath must contain at most 1000 characters") String> changedFilePaths
) {

    public InstructionContextRequest toInstructionContextRequest() {
        return new InstructionContextRequest(List.of(new InstructionRepositoryScope(
                repositoryKey,
                ref,
                changedFilePaths
        )));
    }
}
