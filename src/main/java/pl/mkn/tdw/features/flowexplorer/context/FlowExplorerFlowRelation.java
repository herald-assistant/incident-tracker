package pl.mkn.tdw.features.flowexplorer.context;

public record FlowExplorerFlowRelation(
        String from,
        String to,
        String kind,
        String reason,
        String confidence
) {
}
