package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.Objects;

public record GitLabAngularRouteBranchSliceRequest(
        GitLabFrontendRepositoryScope scope,
        String screenId,
        String expectedRevision,
        Boolean includeDescendantRoutes,
        Integer maxCharacters
) {

    public GitLabAngularRouteBranchSliceRequest {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        if (!StringUtils.hasText(screenId)) {
            throw new IllegalArgumentException("screenId must not be blank");
        }
        screenId = screenId.trim();
        expectedRevision = StringUtils.hasText(expectedRevision) ? expectedRevision.trim() : null;
    }
}
