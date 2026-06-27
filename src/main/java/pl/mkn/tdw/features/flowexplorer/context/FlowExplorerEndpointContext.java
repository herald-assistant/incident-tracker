package pl.mkn.tdw.features.flowexplorer.context;

import java.util.List;

public record FlowExplorerEndpointContext(
        String endpointId,
        List<String> httpMethods,
        String path,
        String pathExpression,
        String controllerClass,
        String handlerMethod,
        String filePath,
        int lineStart,
        int lineEnd,
        String confidence
) {

    public FlowExplorerEndpointContext {
        httpMethods = httpMethods != null ? List.copyOf(httpMethods) : List.of();
        lineStart = Math.max(0, lineStart);
        lineEnd = Math.max(lineStart, lineEnd);
    }
}
