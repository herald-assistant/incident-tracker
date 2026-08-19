package pl.mkn.tdw.features.uiexplorer.context;

public record UiExplorerContextMetrics(
        int sourceFileCount,
        int sourceCharactersRead,
        int returnedSliceCount,
        int returnedCharacters,
        int omittedCharacters,
        int omittedFileCount,
        int relationCount,
        int unresolvedFrontierCount
) {
}
