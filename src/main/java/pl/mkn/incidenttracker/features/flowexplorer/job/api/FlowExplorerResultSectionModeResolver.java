package pl.mkn.incidenttracker.features.flowexplorer.job.api;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

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

    public static List<FlowExplorerResultSectionModeAssignment> resolve(
            List<FlowExplorerFocusArea> focusAreas,
            List<FlowExplorerSectionModeRequest> sectionModes
    ) {
        var requestedModes = requestedModeIndex(sectionModes);
        if (requestedModes.isEmpty()) {
            return resolve(focusAreas);
        }
        return List.of(FlowExplorerResultSectionId.values()).stream()
                .map(sectionId -> new FlowExplorerResultSectionModeAssignment(
                        sectionId,
                        sectionId.title(),
                        requestedModes.getOrDefault(sectionId, FlowExplorerResultSectionMode.COMPACT)
                ))
                .toList();
    }

    public static List<FlowExplorerResultSectionModeAssignment> activeOnly(
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        return (sectionModes != null ? sectionModes : List.<FlowExplorerResultSectionModeAssignment>of()).stream()
                .filter(Objects::nonNull)
                .filter(assignment -> assignment.mode() != FlowExplorerResultSectionMode.OFF)
                .toList();
    }

    public static List<FlowExplorerFocusArea> deepFocusAreas(
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        return (sectionModes != null ? sectionModes : List.<FlowExplorerResultSectionModeAssignment>of()).stream()
                .filter(Objects::nonNull)
                .filter(assignment -> assignment.mode() == FlowExplorerResultSectionMode.DEEP)
                .map(FlowExplorerResultSectionModeAssignment::id)
                .filter(Objects::nonNull)
                .map(FlowExplorerResultSectionId::focusArea)
                .toList();
    }

    private static EnumMap<FlowExplorerResultSectionId, FlowExplorerResultSectionMode> requestedModeIndex(
            List<FlowExplorerSectionModeRequest> sectionModes
    ) {
        var index = new EnumMap<FlowExplorerResultSectionId, FlowExplorerResultSectionMode>(
                FlowExplorerResultSectionId.class
        );
        for (var selection : sectionModes != null ? sectionModes : List.<FlowExplorerSectionModeRequest>of()) {
            if (selection == null || selection.id() == null || selection.mode() == null) {
                continue;
            }
            index.put(selection.id(), selection.mode());
        }
        return index;
    }
}
