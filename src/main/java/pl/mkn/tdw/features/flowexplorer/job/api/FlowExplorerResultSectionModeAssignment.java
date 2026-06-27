package pl.mkn.tdw.features.flowexplorer.job.api;

public record FlowExplorerResultSectionModeAssignment(
        FlowExplorerResultSectionId id,
        String title,
        FlowExplorerResultSectionMode mode
) {
}
