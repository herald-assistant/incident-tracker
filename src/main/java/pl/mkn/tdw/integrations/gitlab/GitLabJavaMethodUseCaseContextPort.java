package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.usecase.GitLabJavaMethodUseCaseContextRequest;
import pl.mkn.tdw.integrations.gitlab.contract.usecase.GitLabJavaMethodUseCaseContextResult;

public interface GitLabJavaMethodUseCaseContextPort {

    GitLabJavaMethodUseCaseContextResult buildContext(
            String group,
            String branch,
            GitLabJavaMethodUseCaseContextRequest request
    );
}
