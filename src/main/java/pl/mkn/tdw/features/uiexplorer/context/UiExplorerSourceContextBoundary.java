package pl.mkn.tdw.features.uiexplorer.context;

public record UiExplorerSourceContextBoundary(
        int repositoryFileCount,
        int scannedRouteFileCount,
        int returnedContextFileCount,
        int totalReturnedCharacters,
        boolean inventoryTruncated,
        boolean routeCatalogTruncated,
        boolean contextTruncated,
        int maxInventoryFiles,
        int maxRouteFiles,
        int maxRouteEntries,
        int maxContextFiles,
        int maxFileCharacters,
        int maxTotalCharacters,
        int maxTraversalDepth
) {
}
