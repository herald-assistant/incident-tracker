package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record GitLabEndpointUseCaseTypeHierarchyIndex(
        Map<String, List<String>> directParentsByType,
        Map<String, List<String>> childrenByParent
) {
    GitLabEndpointUseCaseTypeHierarchyIndex {
        directParentsByType = copyMap(directParentsByType);
        childrenByParent = copyMap(childrenByParent);
    }

    static GitLabEndpointUseCaseTypeHierarchyIndex from(List<GitLabEndpointUseCaseTypeInfo> types) {
        var directParentsByType = new LinkedHashMap<String, List<String>>();
        var childrenByParent = new LinkedHashMap<String, java.util.ArrayList<String>>();

        for (var type : types != null ? types : List.<GitLabEndpointUseCaseTypeInfo>of()) {
            var parents = type.directParentTypes();
            directParentsByType.put(type.fqn(), parents);
            for (var parent : parents) {
                childrenByParent.computeIfAbsent(parent, ignored -> new java.util.ArrayList<>())
                        .add(type.fqn());
            }
        }

        return new GitLabEndpointUseCaseTypeHierarchyIndex(
                directParentsByType,
                copyMutableMap(childrenByParent)
        );
    }

    private static Map<String, List<String>> copyMutableMap(Map<String, java.util.ArrayList<String>> source) {
        var copy = new LinkedHashMap<String, List<String>>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return copy;
    }

    private static Map<String, List<String>> copyMap(Map<String, List<String>> source) {
        var copy = new LinkedHashMap<String, List<String>>();
        if (source != null) {
            source.forEach((key, value) -> copy.put(key, value != null ? List.copyOf(value) : List.of()));
        }
        return Map.copyOf(copy);
    }
}
