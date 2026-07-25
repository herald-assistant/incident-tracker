package pl.mkn.tdw.integrations.gitlab.instructions;

public record InstructionRepositoryFile(
        String repositoryKey,
        String ref,
        String path,
        boolean exists,
        String content,
        boolean truncated,
        String limitation
) {

    public static InstructionRepositoryFile missing(String repositoryKey, String ref, String path) {
        return new InstructionRepositoryFile(repositoryKey, ref, path, false, "", false, null);
    }

    public static InstructionRepositoryFile failed(String repositoryKey, String ref, String path, String limitation) {
        return new InstructionRepositoryFile(repositoryKey, ref, path, false, "", false, limitation);
    }
}
