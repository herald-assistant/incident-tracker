package pl.mkn.tdw.integrations.gitlab;

public interface GitLabExactRepositoryPort {

    boolean branchExists(String connectionId, String projectPath, String branch);

    GitLabExactFileContent readFile(
            String connectionId,
            String projectPath,
            String ref,
            String filePath,
            int maxCharacters
    );

    GitLabExactFileMetadata readFileMetadata(
            String connectionId,
            String projectPath,
            String ref,
            String filePath
    );
}
