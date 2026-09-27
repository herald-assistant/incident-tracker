package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryTreeSlice;

public interface GitLabRepositoryTreeExplorerPort {

    GitLabRepositoryTreeSlice explore(
            String group, String project, String commit, String path, String cursor
    );
}
