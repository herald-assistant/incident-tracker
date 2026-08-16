package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.Objects;

public record GitLabFrontendScreenIdentity(
        String screenId,
        String routeNodeId,
        String routePattern,
        String outlet,
        GitLabFrontendRouteTarget viewTarget
) {

    public GitLabFrontendScreenIdentity {
        screenId = required(screenId, "screenId");
        routeNodeId = required(routeNodeId, "routeNodeId");
        routePattern = required(routePattern, "routePattern");
        outlet = StringUtils.hasText(outlet) ? outlet.trim() : "primary";
        viewTarget = Objects.requireNonNull(viewTarget, "viewTarget must not be null");
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
