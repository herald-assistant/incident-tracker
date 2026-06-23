package pl.mkn.incidenttracker.features.flowexplorer.job.api;

public record FlowExplorerSectionModeRequest(
        FlowExplorerResultSectionId id,
        FlowExplorerResultSectionMode mode
) {
}
