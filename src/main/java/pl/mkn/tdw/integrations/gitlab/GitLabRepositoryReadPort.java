package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileChunk;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileMetadata;

public interface GitLabRepositoryReadPort {

    GitLabRepositoryFileContent readFile(
            String group, String projectName, String branch, String filePath, int maxCharacters);

    GitLabRepositoryFileContent readFileBounded(
            String group, String projectName, String revision, String filePath, int maxBytes);

    GitLabRepositoryFileContent readFileComplete(
            String group, String projectName, String revision, String filePath);

    GitLabRepositoryFileMetadata readFileMetadata(
            String group, String projectName, String branch, String filePath);

    GitLabRepositoryFileChunk readFileChunk(
            String group, String projectName, String branch, String filePath,
            int startLine, int endLine, int maxCharacters);
}
