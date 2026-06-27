package pl.mkn.tdw.features.flowexplorer.context;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;

public record FlowExplorerRepositoryScopeRepository(
        String repositoryId,
        String projectName,
        String projectPath,
        Integer priority,
        String reason,
        OperationalContextRepository repository
) {
}
