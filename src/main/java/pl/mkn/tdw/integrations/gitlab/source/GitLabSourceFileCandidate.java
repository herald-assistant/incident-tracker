package pl.mkn.tdw.integrations.gitlab.source;

public record GitLabSourceFileCandidate(
        String path,
        int score
) {
}
