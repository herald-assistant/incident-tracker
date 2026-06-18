package pl.mkn.incidenttracker.features.flowexplorer.context;

import java.util.List;

public record FlowExplorerSnippetCard(
        String id,
        String projectName,
        String filePath,
        String role,
        List<FlowExplorerFlowMethod> methods,
        int requestedStartLine,
        int requestedEndLine,
        int returnedStartLine,
        int returnedEndLine,
        int totalLines,
        boolean truncated,
        String reason,
        String content,
        int characterCount,
        List<String> limitations
) {

    public FlowExplorerSnippetCard {
        methods = methods != null ? List.copyOf(methods) : List.of();
        content = content != null ? content : "";
        characterCount = content.length();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
