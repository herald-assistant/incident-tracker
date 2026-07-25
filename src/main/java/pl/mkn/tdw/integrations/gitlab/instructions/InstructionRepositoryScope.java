package pl.mkn.tdw.integrations.gitlab.instructions;

import java.util.List;

public record InstructionRepositoryScope(
        String repositoryKey,
        String ref,
        List<String> changedFilePaths
) {

    public InstructionRepositoryScope {
        changedFilePaths = changedFilePaths != null ? List.copyOf(changedFilePaths) : List.of();
    }
}
