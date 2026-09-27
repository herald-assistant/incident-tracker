package pl.mkn.tdw.integrations.gitlab.contract.instructions;

public record InstructionRepositoryInventoryRequest(
        String repositoryKey,
        String ref
) {
}
