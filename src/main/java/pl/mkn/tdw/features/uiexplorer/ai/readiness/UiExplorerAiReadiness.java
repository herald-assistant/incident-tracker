package pl.mkn.tdw.features.uiexplorer.ai.readiness;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;

import java.util.List;

public record UiExplorerAiReadiness(
        UiExplorerAiReadinessStatus status,
        List<UiExplorerSectionId> activeSections,
        boolean fallbackToolsRequired,
        List<String> limitations
) {

    public UiExplorerAiReadiness {
        activeSections = activeSections != null ? List.copyOf(activeSections) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public boolean executable() {
        return status != UiExplorerAiReadinessStatus.BLOCKED;
    }
}
