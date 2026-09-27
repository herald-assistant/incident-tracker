package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryProjectCandidate;

import java.util.List;

public interface GitLabProjectSearchPort {

    List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints);
}
