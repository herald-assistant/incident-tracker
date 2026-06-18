package pl.mkn.incidenttracker.features.flowexplorer.context;

import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;

public record FlowExplorerRepositoryScopeRepository(
        String repositoryId,
        String projectName,
        String projectPath,
        Integer priority,
        String reason,
        OperationalContextRepository repository
) {
}
