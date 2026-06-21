package pl.mkn.incidenttracker.features.flowexplorer.job.api;

import java.util.LinkedHashSet;
import java.util.List;

public final class FlowExplorerResultSectionModeResolver {

    private FlowExplorerResultSectionModeResolver() {
    }

    public static List<FlowExplorerResultSectionModeAssignment> resolve(List<FlowExplorerFocusArea> focusAreas) {
        var focused = new LinkedHashSet<>(focusAreas != null ? focusAreas : List.<FlowExplorerFocusArea>of());
        return List.of(FlowExplorerResultSectionId.values()).stream()
                .map(sectionId -> new FlowExplorerResultSectionModeAssignment(
                        sectionId,
                        sectionId.title(),
                        focused.contains(sectionId.focusArea())
                                ? FlowExplorerResultSectionMode.DEEP
                                : FlowExplorerResultSectionMode.COMPACT
                ))
                .toList();
    }
}
