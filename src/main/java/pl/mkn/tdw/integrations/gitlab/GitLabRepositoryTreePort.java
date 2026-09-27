package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFilePage;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryTreePage;

import java.util.List;

public interface GitLabRepositoryTreePort {

    List<GitLabRepositoryFile> listRepositoryFiles(
            String group, String projectName, String branch, String pathPrefix);

    GitLabRepositoryFilePage listRepositoryFilesPage(
            String group, String projectName, String revision, String pathPrefix, String cursor, int maxResults);

    GitLabRepositoryTreePage listRepositoryTreeChildrenPage(
            String group, String projectName, String revision, String directory, String cursor, int maxEntries);
}
