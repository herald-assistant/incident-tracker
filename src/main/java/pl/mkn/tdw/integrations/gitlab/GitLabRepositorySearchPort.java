package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositorySearchRequest;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositorySearchResponse;

public interface GitLabRepositorySearchPort {

    GitLabRepositorySearchResponse search(GitLabRepositorySearchRequest request);
}
