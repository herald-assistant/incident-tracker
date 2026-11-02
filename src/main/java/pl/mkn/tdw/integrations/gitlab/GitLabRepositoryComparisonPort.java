package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison;

public interface GitLabRepositoryComparisonPort {
    GitLabRepositoryComparison compare(String group, String project, String baselineCommit, String targetCommit);
}