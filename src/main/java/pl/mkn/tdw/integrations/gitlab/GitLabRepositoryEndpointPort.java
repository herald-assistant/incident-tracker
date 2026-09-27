package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryEndpointListRequest;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryEndpointListResult;

public interface GitLabRepositoryEndpointPort {

    GitLabRepositoryEndpointListResult listEndpoints(GitLabRepositoryEndpointListRequest request);
}
