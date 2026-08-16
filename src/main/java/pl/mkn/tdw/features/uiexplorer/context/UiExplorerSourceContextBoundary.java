package pl.mkn.tdw.features.uiexplorer.context;

public record UiExplorerSourceContextBoundary(
        int visitedRouteNodeCount,
        int visitedRouteFileCount,
        int graphSourceReadCount,
        int aliasResolutionCount,
        int unresolvedEdgeCount,
        int returnedContextFileCount,
        int totalReturnedCharacters,
        boolean graphLimitReached,
        boolean contextLimitReached,
        int maxRouteNodes,
        int maxRouteFiles,
        int maxSourceReads,
        int maxAliasResolutions,
        int maxImportDepth,
        int maxComponentDepth,
        int maxContextFiles,
        int maxFileCharacters,
        int maxTotalCharacters
) {
}
