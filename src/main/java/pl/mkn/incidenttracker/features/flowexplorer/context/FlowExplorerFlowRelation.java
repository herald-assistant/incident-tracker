package pl.mkn.incidenttracker.features.flowexplorer.context;

public record FlowExplorerFlowRelation(
        String from,
        String to,
        String kind,
        String reason,
        String confidence
) {
}
