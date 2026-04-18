package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

public record GitLabSourceFileCandidate(
        String path,
        int score
) {
}
