package pl.mkn.tdw.integrations.gitlab.instructions;

public record InstructionRepositoryInventoryRequest(
        String repositoryKey,
        String ref
) {
}
