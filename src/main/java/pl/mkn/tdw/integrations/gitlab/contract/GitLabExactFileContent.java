package pl.mkn.tdw.integrations.gitlab.contract;

public record GitLabExactFileContent(
        String connectionId,
        String projectPath,
        String ref,
        String filePath,
        String content,
        int returnedCharacters,
        boolean truncated
) {
}
