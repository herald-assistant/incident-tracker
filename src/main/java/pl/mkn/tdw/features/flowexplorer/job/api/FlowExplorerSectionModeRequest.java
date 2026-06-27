package pl.mkn.tdw.features.flowexplorer.job.api;

public record FlowExplorerSectionModeRequest(
        FlowExplorerResultSectionId id,
        FlowExplorerResultSectionMode mode
) {
}
