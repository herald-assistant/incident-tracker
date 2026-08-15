package pl.mkn.tdw.features.uiexplorer.contract;

import java.util.Objects;

public record UiExplorerSectionModeAssignment(
        UiExplorerSectionId sectionId,
        UiExplorerSectionMode mode
) {

    public UiExplorerSectionModeAssignment {
        Objects.requireNonNull(sectionId, "sectionId");
        Objects.requireNonNull(mode, "mode");
    }
}

