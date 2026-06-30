package pl.mkn.tdw.features.flowexplorer.ai.report;

import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;

import java.util.ArrayList;
import java.util.List;

public final class FlowExplorerReportSectionIds {

    public static final String OVERVIEW = "OVERVIEW";

    private FlowExplorerReportSectionIds() {
    }

    public static List<String> activeReportSectionIds(
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        var sectionIds = new ArrayList<String>();
        sectionIds.add(OVERVIEW);
        FlowExplorerResultSectionModeResolver.activeOnly(sectionModes).stream()
                .map(assignment -> assignment.id() != null ? assignment.id().name() : null)
                .filter(value -> value != null && !value.isBlank())
                .forEach(sectionIds::add);
        return List.copyOf(sectionIds);
    }
}
