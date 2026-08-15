package pl.mkn.tdw.features.uiexplorer.catalog;

public record UiExplorerConfigurationFinding(
        String severity,
        String code,
        String message,
        String entityType,
        String entityId
) {
}

