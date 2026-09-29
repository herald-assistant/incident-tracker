package pl.mkn.tdw.integrations.gitlab.service;

import org.springframework.stereotype.Service;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryReadPort;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFilePort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabVerifiedFile;

@Service
public class GitLabVerifiedRepositoryFileService implements GitLabVerifiedRepositoryFilePort {

    @Override
    public GitLabVerifiedFile read(GitLabRepositoryReadPort repositoryPort, String group,
                                   String projectName, String commitId, String filePath, int maxBytes) {
        return GitLabVerifiedRepositoryFileReader.read(
                repositoryPort, group, projectName, commitId, filePath, maxBytes);
    }

    @Override
    public GitLabVerifiedFile readComplete(GitLabRepositoryReadPort repositoryPort, String group,
                                           String projectName, String commitId, String filePath) {
        return GitLabVerifiedRepositoryFileReader.readComplete(
                repositoryPort, group, projectName, commitId, filePath);
    }

    @Override
    public GitLabVerifiedFile readBranch(GitLabRepositoryReadPort repositoryPort, String group,
                                          String projectName, String branch, String filePath, int maxBytes) {
        return GitLabVerifiedRepositoryFileReader.readBranch(
                repositoryPort, group, projectName, branch, filePath, maxBytes);
    }

    @Override
    public GitLabVerifiedFile readCompleteBranch(GitLabRepositoryReadPort repositoryPort, String group,
                                                  String projectName, String branch, String filePath) {
        return GitLabVerifiedRepositoryFileReader.readCompleteBranch(
                repositoryPort, group, projectName, branch, filePath);
    }
}
