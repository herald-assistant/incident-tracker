package pl.mkn.incidenttracker.integrations.gitlab.source;

public record GitLabSourceFileCandidate(
        String path,
        int score
) {
}
