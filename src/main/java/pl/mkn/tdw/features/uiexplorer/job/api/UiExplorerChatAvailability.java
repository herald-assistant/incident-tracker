package pl.mkn.tdw.features.uiexplorer.job.api;

public record UiExplorerChatAvailability(
        boolean available,
        String code,
        String message
) {
}
