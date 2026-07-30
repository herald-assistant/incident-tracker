package pl.mkn.tdw.features.runtimeconfigurationverification.deep.model;

import java.util.List;

public record RuntimeConfigurationDeepCoverage(
        int repositoriesPlanned,
        int repositoriesSearched,
        int keysSearched,
        int filesInspected,
        int codeMatches,
        List<String> unavailableRepositoryIds,
        List<String> systemsWithoutCodeSearchScope
) {

    public RuntimeConfigurationDeepCoverage {
        unavailableRepositoryIds = unavailableRepositoryIds != null
                ? List.copyOf(unavailableRepositoryIds)
                : List.of();
        systemsWithoutCodeSearchScope = systemsWithoutCodeSearchScope != null
                ? List.copyOf(systemsWithoutCodeSearchScope)
                : List.of();
    }
}
