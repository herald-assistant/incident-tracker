package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendReachabilityComponentLevel(
        int depth,
        List<GitLabFrontendReachabilityComponent> components
) {
    public GitLabFrontendReachabilityComponentLevel {
        components = components != null ? List.copyOf(components) : List.of();
    }
}
