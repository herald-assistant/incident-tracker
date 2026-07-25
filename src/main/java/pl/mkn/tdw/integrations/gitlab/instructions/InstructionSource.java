package pl.mkn.tdw.integrations.gitlab.instructions;

import java.util.List;

public record InstructionSource(
        String repositoryKey,
        String ref,
        String path,
        String kind,
        String content,
        boolean truncated,
        String referencedBy,
        List<String> applicableChangedFiles
) {

    public InstructionSource {
        applicableChangedFiles = applicableChangedFiles != null ? List.copyOf(applicableChangedFiles) : List.of();
    }
}
