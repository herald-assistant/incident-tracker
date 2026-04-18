package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import java.util.List;

public record GitLabRepositorySearchResponse(
        String group,
        String branch,
        List<String> projectHints,
        List<GitLabRepositoryProjectCandidate> projectCandidates,
        List<GitLabRepositoryFileCandidate> fileCandidates,
        String message
) {
}
