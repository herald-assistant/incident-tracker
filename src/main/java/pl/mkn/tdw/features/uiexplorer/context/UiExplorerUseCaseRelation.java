package pl.mkn.tdw.features.uiexplorer.context;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;

public record UiExplorerUseCaseRelation(
        String from,
        String to,
        String kind,
        String symbol,
        String confidence,
        UiExplorerSourceReference source
) {
}
