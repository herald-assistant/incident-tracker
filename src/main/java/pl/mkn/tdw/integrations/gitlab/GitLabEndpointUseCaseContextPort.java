package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.usecase.GitLabEndpointUseCaseContextRequest;
import pl.mkn.tdw.integrations.gitlab.contract.usecase.GitLabEndpointUseCaseContextResult;

public interface GitLabEndpointUseCaseContextPort {

    GitLabEndpointUseCaseContextResult buildContext(
            String group,
            String branch,
            GitLabEndpointUseCaseContextRequest request
    );
}
