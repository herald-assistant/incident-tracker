package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record GitLabEndpointUseCaseCodeIndex(
        GitLabEndpointUseCaseSourceSnapshot sourceSnapshot,
        GitLabEndpointUseCaseIndexStatus indexStatus,
        List<GitLabEndpointUseCaseTypeInfo> types,
        Map<String, GitLabEndpointUseCaseTypeInfo> typesByFqn,
        Map<String, List<GitLabEndpointUseCaseTypeInfo>> typesBySimpleName,
        GitLabEndpointUseCaseTypeHierarchyIndex hierarchyIndex,
        GitLabEndpointUseCaseMethodCallIndex methodCallIndex,
        List<GitLabEndpointUseCaseWarning> warnings
) {
    GitLabEndpointUseCaseCodeIndex {
        indexStatus = indexStatus != null ? indexStatus : GitLabEndpointUseCaseIndexStatus.NOT_BUILT;
        types = types != null ? List.copyOf(types) : List.of();
        typesByFqn = copyTypeMap(typesByFqn);
        typesBySimpleName = copySimpleNameMap(typesBySimpleName);
        hierarchyIndex = hierarchyIndex != null
                ? hierarchyIndex
                : GitLabEndpointUseCaseTypeHierarchyIndex.from(types);
        methodCallIndex = methodCallIndex != null
                ? methodCallIndex
                : GitLabEndpointUseCaseMethodCallIndex.from(List.of());
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    Optional<GitLabEndpointUseCaseTypeInfo> findType(String fqn) {
        return Optional.ofNullable(typesByFqn.get(fqn));
    }

    static GitLabEndpointUseCaseCodeIndex from(
            GitLabEndpointUseCaseSourceSnapshot sourceSnapshot,
            GitLabEndpointUseCaseIndexStatus indexStatus,
            List<GitLabEndpointUseCaseTypeInfo> types,
            List<GitLabEndpointUseCaseMethodCallInfo> calls,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var safeTypes = types != null ? List.copyOf(types) : List.<GitLabEndpointUseCaseTypeInfo>of();
        var typesByFqn = new LinkedHashMap<String, GitLabEndpointUseCaseTypeInfo>();
        var typesBySimpleName = new LinkedHashMap<String, java.util.ArrayList<GitLabEndpointUseCaseTypeInfo>>();
        for (var type : safeTypes) {
            typesByFqn.put(type.fqn(), type);
            typesBySimpleName.computeIfAbsent(type.simpleName(), ignored -> new java.util.ArrayList<>())
                    .add(type);
        }

        var immutableTypesBySimpleName = new LinkedHashMap<String, List<GitLabEndpointUseCaseTypeInfo>>();
        typesBySimpleName.forEach((simpleName, matchedTypes) ->
                immutableTypesBySimpleName.put(simpleName, List.copyOf(matchedTypes)));

        return new GitLabEndpointUseCaseCodeIndex(
                sourceSnapshot,
                indexStatus,
                safeTypes,
                typesByFqn,
                immutableTypesBySimpleName,
                GitLabEndpointUseCaseTypeHierarchyIndex.from(safeTypes),
                GitLabEndpointUseCaseMethodCallIndex.from(calls),
                warnings
        );
    }

    private static Map<String, GitLabEndpointUseCaseTypeInfo> copyTypeMap(
            Map<String, GitLabEndpointUseCaseTypeInfo> source
    ) {
        var copy = new LinkedHashMap<String, GitLabEndpointUseCaseTypeInfo>();
        if (source != null) {
            copy.putAll(source);
        }
        return Map.copyOf(copy);
    }

    private static Map<String, List<GitLabEndpointUseCaseTypeInfo>> copySimpleNameMap(
            Map<String, List<GitLabEndpointUseCaseTypeInfo>> source
    ) {
        var copy = new LinkedHashMap<String, List<GitLabEndpointUseCaseTypeInfo>>();
        if (source != null) {
            source.forEach((key, value) -> copy.put(key, value != null ? List.copyOf(value) : List.of()));
        }
        return Map.copyOf(copy);
    }
}
