package pl.mkn.tdw.integrations.gitlab.contract.source;

public record GitLabSourceFileCandidate(
        String path,
        int score
) {
}
