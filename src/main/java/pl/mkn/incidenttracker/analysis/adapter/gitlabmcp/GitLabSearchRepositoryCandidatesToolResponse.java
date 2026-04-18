package pl.mkn.incidenttracker.analysis.adapter.gitlabmcp;

import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileCandidate;

import java.util.List;

public record GitLabSearchRepositoryCandidatesToolResponse(
        List<GitLabRepositoryFileCandidate> candidates
) {
}
