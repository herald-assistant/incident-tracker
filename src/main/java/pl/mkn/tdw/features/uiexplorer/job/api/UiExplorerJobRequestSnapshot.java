package pl.mkn.tdw.features.uiexplorer.job.api;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerProfile;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionModeAssignment;

import java.util.List;

public record UiExplorerJobRequestSnapshot(
        String systemId,
        String systemLabel,
        String branch,
        String screenId,
        String sourceRevision,
        UiExplorerProfile profile,
        List<UiExplorerSectionModeAssignment> sectionModes,
        String scenarioDescription,
        String aiModel,
        String reasoningEffort
) {

    public UiExplorerJobRequestSnapshot {
        sectionModes = sectionModes != null ? List.copyOf(sectionModes) : List.of();
    }
}
