package pl.mkn.tdw.features.uiexplorer.ai.response;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;

import java.util.List;

public record UiExplorerAiParseResult(
        UiExplorerAiParseStatus status,
        UiExplorerResultResponse result,
        List<String> limitations
) {

    public UiExplorerAiParseResult {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
