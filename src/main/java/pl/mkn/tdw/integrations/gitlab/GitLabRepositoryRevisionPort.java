package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryRevision;

public interface GitLabRepositoryRevisionPort {

    GitLabRepositoryRevision resolveRevision(String group, String projectName, String ref);

    boolean branchExists(String group, String projectName, String branch);

    boolean refExists(String group, String projectName, String ref);
}
