package pl.mkn.tdw.features.uiexplorer.catalog;

public record UiExplorerScreenCatalogDiagnostic(
        String severity,
        String code,
        String message,
        String sourcePath
) {
}
