package pl.mkn.tdw.features.uiexplorer.report;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;

import java.util.List;

public final class UiExplorerReportSectionIds {

    private UiExplorerReportSectionIds() {
    }

    public static List<String> activeSectionIds(UiExplorerJobStartRequest request) {
        if (request == null) {
            return List.of();
        }
        return request.resolvedSectionModes().stream()
                .filter(assignment -> assignment.mode() != UiExplorerSectionMode.OFF)
                .map(assignment -> assignment.sectionId().name())
                .toList();
    }
}
