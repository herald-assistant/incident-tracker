package pl.mkn.incidenttracker.features.flowexplorer.context;

import java.util.List;

public record FlowExplorerFlowNode(
        String id,
        String role,
        String filePath,
        List<FlowExplorerFlowMethod> methods,
        String reason,
        String confidence,
        List<String> limitations
) {

    public FlowExplorerFlowNode {
        methods = methods != null ? List.copyOf(methods) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
