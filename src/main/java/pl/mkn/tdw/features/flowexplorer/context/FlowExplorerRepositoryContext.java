package pl.mkn.tdw.features.flowexplorer.context;

import java.util.List;

public record FlowExplorerRepositoryContext(
        String repositoryId,
        String projectName,
        String projectPath,
        String resolvedRef,
        boolean attempted,
        boolean selected,
        List<String> limitations
) {

    public FlowExplorerRepositoryContext {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
