package pl.mkn.tdw.features.uiexplorer.catalog;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;

import java.util.List;
import java.time.Instant;

public record UiExplorerScreenCatalog(
        String systemId,
        String systemLabel,
        Instant dataCollectedAt,
        UiExplorerSourceRevision sourceRevision,
        UiExplorerScreenCatalogStatus status,
        List<UiExplorerScreenCatalogEntry> screens,
        List<UiExplorerScreenCatalogDiagnostic> diagnostics,
        List<String> limitations,
        UiExplorerScreenCatalogBoundary boundary
) {

    public UiExplorerScreenCatalog {
        screens = screens != null ? List.copyOf(screens) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
