package pl.mkn.incidenttracker.features.flowexplorer.context;

import java.util.List;

public record FlowExplorerOpenApiContractResult(
        List<FlowExplorerOpenApiEndpointContract> contracts,
        List<String> limitations
) {

    public FlowExplorerOpenApiContractResult {
        contracts = contracts != null ? List.copyOf(contracts) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    public static FlowExplorerOpenApiContractResult empty() {
        return new FlowExplorerOpenApiContractResult(List.of(), List.of());
    }
}
