package pl.mkn.incidenttracker.features.flowexplorer.ai;

import java.util.List;

public record FlowExplorerAiEndpointContract(
        String method,
        String path,
        String purpose,
        List<String> request,
        List<String> response,
        List<String> parameters
) {

    public FlowExplorerAiEndpointContract {
        request = request != null ? List.copyOf(request) : List.of();
        response = response != null ? List.copyOf(response) : List.of();
        parameters = parameters != null ? List.copyOf(parameters) : List.of();
    }
}
