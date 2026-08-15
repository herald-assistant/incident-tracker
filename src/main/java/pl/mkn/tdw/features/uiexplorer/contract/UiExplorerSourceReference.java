package pl.mkn.tdw.features.uiexplorer.contract;

public record UiExplorerSourceReference(
        String repository,
        String path,
        String symbol,
        Integer startLine,
        Integer endLine
) {
}

