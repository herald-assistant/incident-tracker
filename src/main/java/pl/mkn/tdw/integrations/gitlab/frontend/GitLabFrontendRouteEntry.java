package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendRouteEntry(
        String screenId,
        String label,
        String routePattern,
        String parentRoutePattern,
        GitLabFrontendRouteEntryKind kind,
        GitLabFrontendDiscoveryStatus status,
        boolean lazyLoaded,
        List<String> guards,
        List<String> routeParameters,
        String redirectTarget,
        String viewSymbol,
        String viewSourcePath,
        GitLabFrontendSourceReference routeSource,
        List<String> limitations
) {

    public GitLabFrontendRouteEntry {
        guards = guards != null ? List.copyOf(guards) : List.of();
        routeParameters = routeParameters != null ? List.copyOf(routeParameters) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}

