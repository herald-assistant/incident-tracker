package pl.mkn.incidenttracker.features.flowexplorer.context;

import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;

import java.util.List;

public record FlowExplorerRepositoryScope(
        OperationalContextSystem system,
        String requestedBranch,
        String resolvedRef,
        String gitLabGroup,
        int repositoryRefCount,
        List<FlowExplorerRepositoryScopeRepository> repositories,
        List<String> limitations
) {

    public FlowExplorerRepositoryScope {
        repositories = repositories != null ? List.copyOf(repositories) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
