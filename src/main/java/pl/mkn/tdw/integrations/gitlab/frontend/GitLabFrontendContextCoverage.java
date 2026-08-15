package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendContextCoverage(
        String category,
        GitLabFrontendCoverageStatus status,
        String detail
) {
}

