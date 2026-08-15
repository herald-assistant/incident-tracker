package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

public record GitLabFrontendScreenContextRequest(
        GitLabFrontendRepositoryScope scope,
        String screenId,
        String expectedCommitId,
        GitLabFrontendDiscoveryLimits limits
) {

    public GitLabFrontendScreenContextRequest(
            GitLabFrontendRepositoryScope scope,
            String screenId,
            GitLabFrontendDiscoveryLimits limits
    ) {
        this(scope, screenId, null, limits);
    }

    public GitLabFrontendScreenContextRequest {
        if (scope == null) {
            throw new IllegalArgumentException("scope must be provided");
        }
        if (!StringUtils.hasText(screenId)) {
            throw new IllegalArgumentException("screenId must not be blank");
        }
        screenId = screenId.trim();
        expectedCommitId = StringUtils.hasText(expectedCommitId) ? expectedCommitId.trim() : null;
        limits = limits != null ? limits : GitLabFrontendDiscoveryLimits.defaults();
    }
}
