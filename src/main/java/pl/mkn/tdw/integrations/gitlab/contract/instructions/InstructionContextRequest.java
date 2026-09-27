package pl.mkn.tdw.integrations.gitlab.contract.instructions;

import java.util.List;

public record InstructionContextRequest(
        List<InstructionRepositoryScope> scopes
) {

    public InstructionContextRequest {
        scopes = scopes != null ? List.copyOf(scopes) : List.of();
    }
}
