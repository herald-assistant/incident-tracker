package pl.mkn.tdw.features.uiexplorer.context;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;

import java.util.List;

public record UiExplorerSectionContextCoverage(
        UiExplorerSectionId sectionId,
        UiExplorerSectionMode mode,
        UiExplorerCoverageStatus status,
        List<String> sourceCategories,
        String detail
) {

    public UiExplorerSectionContextCoverage {
        sourceCategories = sourceCategories != null ? List.copyOf(sourceCategories) : List.of();
    }
}
