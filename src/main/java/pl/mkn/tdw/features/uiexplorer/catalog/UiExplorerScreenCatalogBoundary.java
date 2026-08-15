package pl.mkn.tdw.features.uiexplorer.catalog;

public record UiExplorerScreenCatalogBoundary(
        int repositoryFileCount,
        int scannedRouteFileCount,
        boolean inventoryTruncated,
        boolean routeCatalogTruncated,
        int maxInventoryFiles,
        int maxRouteFiles,
        int maxRouteEntries
) {
}
