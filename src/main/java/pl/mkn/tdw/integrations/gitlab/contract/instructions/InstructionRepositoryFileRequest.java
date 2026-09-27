package pl.mkn.tdw.integrations.gitlab.contract.instructions;

public record InstructionRepositoryFileRequest(
        String repositoryKey,
        String ref,
        String path,
        int maxCharacters
) {
}
