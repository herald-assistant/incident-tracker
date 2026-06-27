package pl.mkn.tdw.features.flowexplorer.context;

public record FlowExplorerFlowMethod(
        String methodName,
        int lineStart,
        int lineEnd
) {

    public FlowExplorerFlowMethod {
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
    }
}
