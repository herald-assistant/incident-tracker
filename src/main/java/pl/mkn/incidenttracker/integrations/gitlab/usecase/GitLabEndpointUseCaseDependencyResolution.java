package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record GitLabEndpointUseCaseDependencyResolution(
        List<GitLabEndpointUseCaseResolvedDependency> dependencies,
        Map<String, Map<String, GitLabEndpointUseCaseResolvedDependency>> dependenciesByTypeAndMember,
        List<GitLabEndpointUseCaseWarning> warnings
) {
    GitLabEndpointUseCaseDependencyResolution {
        dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        dependenciesByTypeAndMember = copyNestedMap(dependenciesByTypeAndMember);
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    GitLabEndpointUseCaseResolvedDependency findDependency(String declaringType, String memberName) {
        return dependenciesByTypeAndMember.getOrDefault(declaringType, Map.of()).get(memberName);
    }

    static GitLabEndpointUseCaseDependencyResolution from(
            List<GitLabEndpointUseCaseResolvedDependency> dependencies,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var byTypeAndMember = new LinkedHashMap<String, Map<String, GitLabEndpointUseCaseResolvedDependency>>();
        var mutable = new LinkedHashMap<String, LinkedHashMap<String, GitLabEndpointUseCaseResolvedDependency>>();
        for (var dependency : dependencies != null ? dependencies : List.<GitLabEndpointUseCaseResolvedDependency>of()) {
            mutable.computeIfAbsent(dependency.injectionPoint().declaringType(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(dependency.injectionPoint().memberName(), dependency);
        }
        mutable.forEach((type, memberMap) -> byTypeAndMember.put(type, Map.copyOf(memberMap)));
        return new GitLabEndpointUseCaseDependencyResolution(dependencies, byTypeAndMember, warnings);
    }

    private static Map<String, Map<String, GitLabEndpointUseCaseResolvedDependency>> copyNestedMap(
            Map<String, Map<String, GitLabEndpointUseCaseResolvedDependency>> source
    ) {
        var copy = new LinkedHashMap<String, Map<String, GitLabEndpointUseCaseResolvedDependency>>();
        if (source != null) {
            source.forEach((type, memberMap) ->
                    copy.put(type, memberMap != null ? Map.copyOf(memberMap) : Map.of()));
        }
        return Map.copyOf(copy);
    }
}
