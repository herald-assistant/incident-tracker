package pl.mkn.tdw.features.flowexplorer.context;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;

import java.util.List;

public record FlowExplorerRepositoryScopeRepository(
        String repositoryId,
        String projectName,
        String projectPath,
        String scopeId,
        String role,
        Integer priority,
        String reason,
        List<String> readFor,
        String searchMode,
        List<String> pathPrefixes,
        OperationalContextRepository repository
) {

    public FlowExplorerRepositoryScopeRepository {
        readFor = readFor != null ? List.copyOf(readFor) : List.of();
        pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
    }
}
