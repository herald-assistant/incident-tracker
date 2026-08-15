package pl.mkn.tdw.features.uiexplorer.contract;

public record UiExplorerScreenIdentity(
        String systemId,
        String screenId,
        String label,
        String routePattern,
        String navigationContext
) {
}

