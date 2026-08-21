package pl.mkn.tdw.features.uiexplorer.context;

public record UiExplorerReachabilityBoundary(
        int routeSegmentCount,
        int componentCount,
        int dependencyCount,
        int edgeCount,
        int sourceFileCount,
        int sourceCharacters,
        int sliceCharacters,
        int outlineCharacters,
        boolean contextLimitReached
) {
}
