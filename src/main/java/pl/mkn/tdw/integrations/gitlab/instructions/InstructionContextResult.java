package pl.mkn.tdw.integrations.gitlab.instructions;

import java.util.List;

public record InstructionContextResult(
        List<InstructionSource> sources,
        List<String> limitations
) {

    public InstructionContextResult {
        sources = sources != null ? List.copyOf(sources) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
