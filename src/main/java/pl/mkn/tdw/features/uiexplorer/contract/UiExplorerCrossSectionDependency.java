package pl.mkn.tdw.features.uiexplorer.contract;

public record UiExplorerCrossSectionDependency(
        UiExplorerSectionId sourceSection,
        UiExplorerSectionId targetSection,
        String description
) {
}

