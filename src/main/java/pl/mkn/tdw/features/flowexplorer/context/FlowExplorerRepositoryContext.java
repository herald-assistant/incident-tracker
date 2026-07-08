package pl.mkn.tdw.features.flowexplorer.context;

import java.util.List;

public record FlowExplorerRepositoryContext(
        String repositoryId,
        String projectName,
        String projectPath,
        String resolvedRef,
        String searchMode,
        List<String> pathPrefixes,
        boolean attempted,
        boolean selected,
        List<String> limitations
) {

    public FlowExplorerRepositoryContext(
            String repositoryId,
            String projectName,
            String projectPath,
            String resolvedRef,
            boolean attempted,
            boolean selected,
            List<String> limitations
    ) {
        this(repositoryId, projectName, projectPath, resolvedRef, null, List.of(), attempted, selected, limitations);
    }

    public FlowExplorerRepositoryContext {
        pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
