package pl.mkn.tdw.features.uiexplorer.context;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;

public record UiExplorerSourceContextSignal(
        String kind,
        String description,
        String confidence,
        UiExplorerSourceReference source
) {
}
