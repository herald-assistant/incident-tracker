package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabVerifiedFile;

public interface GitLabVerifiedRepositoryFilePort {

    int MAX_FILE_BYTES = 256 * 1024;

    GitLabVerifiedFile read(GitLabRepositoryReadPort repositoryPort, String group,
                            String projectName, String commitId, String filePath, int maxBytes);

    GitLabVerifiedFile readComplete(GitLabRepositoryReadPort repositoryPort, String group,
                                    String projectName, String commitId, String filePath);

    GitLabVerifiedFile readBranch(GitLabRepositoryReadPort repositoryPort, String group,
                                   String projectName, String branch, String filePath, int maxBytes);

    GitLabVerifiedFile readCompleteBranch(GitLabRepositoryReadPort repositoryPort, String group,
                                           String projectName, String branch, String filePath);
}
