package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileCandidate;

import java.util.List;

public record GitLabSearchRepositoryCandidatesToolResponse(
        List<GitLabRepositoryFileCandidate> candidates
) {
}
