package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabBranchPage;

public interface GitLabRepositoryBranchPort {

    GitLabBranchPage listBranches(String projectPath, String search);
}
