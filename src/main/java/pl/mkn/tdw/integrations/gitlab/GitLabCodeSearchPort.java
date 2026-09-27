package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositorySearchQuery;

import java.util.List;

public interface GitLabCodeSearchPort {

    List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query);

    List<GitLabRepositoryFileCandidate> searchRepositoryFilesByContent(
            String group, String projectName, String branch, List<String> searchTerms, int maxResultsPerTerm);
}
