package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record GitLabFrontendRouteGraphEdge(
        String edgeId,
        String sourceNodeId,
        String targetNodeId,
        GitLabFrontendRouteGraphEdgeKind kind,
        GitLabFrontendRouteGraphEdgeStatus status,
        GitLabFrontendRouteTarget target,
        GitLabFrontendSourceReference source,
        List<String> limitations
) {

    public GitLabFrontendRouteGraphEdge {
        edgeId = required(edgeId, "edgeId");
        sourceNodeId = normalize(sourceNodeId);
        targetNodeId = normalize(targetNodeId);
        kind = Objects.requireNonNull(kind, "kind must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        source = Objects.requireNonNull(source, "source must not be null");
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        if (status == GitLabFrontendRouteGraphEdgeStatus.RESOLVED && targetNodeId == null && target == null) {
            throw new IllegalArgumentException("resolved graph edge requires targetNodeId or target");
        }
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
