package pl.mkn.tdw.features.uiexplorer.catalog;

import java.util.List;

public record UiExplorerScreenCatalogEntry(
        String screenId,
        String label,
        String routePattern,
        String parentRoutePattern,
        List<String> componentSelectors,
        String status,
        boolean lazyLoaded,
        List<String> guards,
        List<String> routeParameters,
        List<String> limitations
) {

    public UiExplorerScreenCatalogEntry {
        componentSelectors = componentSelectors != null ? List.copyOf(componentSelectors) : List.of();
        guards = guards != null ? List.copyOf(guards) : List.of();
        routeParameters = routeParameters != null ? List.copyOf(routeParameters) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
