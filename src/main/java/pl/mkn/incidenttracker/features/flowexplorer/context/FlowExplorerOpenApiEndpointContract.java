package pl.mkn.incidenttracker.features.flowexplorer.context;

import java.util.List;

public record FlowExplorerOpenApiEndpointContract(
        String projectName,
        String filePath,
        String httpMethod,
        String endpointPath,
        String matchedPath,
        String operationId,
        String summary,
        String description,
        List<String> tags,
        String sourceRef,
        String content,
        int characterCount,
        boolean truncated,
        List<String> limitations
) {

    public FlowExplorerOpenApiEndpointContract {
        tags = tags != null ? List.copyOf(tags) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
