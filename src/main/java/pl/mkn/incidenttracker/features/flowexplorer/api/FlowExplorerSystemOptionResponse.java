package pl.mkn.incidenttracker.features.flowexplorer.api;

import java.util.List;

public record FlowExplorerSystemOptionResponse(
        String systemId,
        String name,
        String shortName,
        String kind,
        String lifecycleStatus,
        String operationalStatus,
        String criticality,
        String summary,
        List<String> aliases,
        int repositoryCount,
        int codeSearchScopeCount,
        List<String> ownerTeamIds
) {

    public FlowExplorerSystemOptionResponse {
        aliases = aliases != null ? List.copyOf(aliases) : List.of();
        ownerTeamIds = ownerTeamIds != null ? List.copyOf(ownerTeamIds) : List.of();
    }
}
