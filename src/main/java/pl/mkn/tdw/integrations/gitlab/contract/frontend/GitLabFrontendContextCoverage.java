package pl.mkn.tdw.integrations.gitlab.contract.frontend;

public record GitLabFrontendContextCoverage(
        String category,
        GitLabFrontendCoverageStatus status,
        String detail
) {
}

