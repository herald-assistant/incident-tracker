package pl.mkn.tdw.integrations.gitlab.instructions;

import java.util.List;

public record InstructionRepositoryInventory(
        boolean available,
        List<String> paths,
        String limitation
) {

    public InstructionRepositoryInventory {
        paths = paths != null ? List.copyOf(paths) : List.of();
    }

    public static InstructionRepositoryInventory available(List<String> paths) {
        return new InstructionRepositoryInventory(true, paths, null);
    }

    public static InstructionRepositoryInventory unavailable(String limitation) {
        return new InstructionRepositoryInventory(false, List.of(), limitation);
    }
}
