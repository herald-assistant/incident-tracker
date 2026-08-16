package pl.mkn.tdw.features.uiexplorer.catalog;

public record UiExplorerScreenCatalogBoundary(
        int visitedRouteNodeCount,
        int visitedRouteFileCount,
        int sourceReadCount,
        int aliasResolutionCount,
        int unresolvedEdgeCount,
        boolean limitReached,
        int maxRouteNodes,
        int maxRouteFiles,
        int maxSourceReads,
        int maxAliasResolutions,
        int maxImportDepth
) {
}
