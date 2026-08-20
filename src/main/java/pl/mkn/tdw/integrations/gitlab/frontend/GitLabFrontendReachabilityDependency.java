package pl.mkn.tdw.integrations.gitlab.frontend;

import java.util.List;

public record GitLabFrontendReachabilityDependency(
        String dependencyId,
        int discoveryOrder,
        GitLabFrontendReachabilityDependencyKind kind,
        GitLabFrontendReachabilityDependencyCategory category,
        String symbol,
        String sourcePath,
        String moduleSpecifier,
        String status,
        List<String> methods,
        List<String> usedBy,
        List<String> downstreamDependencyIds,
        String sliceContent,
        int sourceCharacters,
        int returnedCharacters,
        boolean truncated,
        List<String> limitations
) {
    public GitLabFrontendReachabilityDependency {
        methods = methods != null ? List.copyOf(methods) : List.of();
        usedBy = usedBy != null ? List.copyOf(usedBy) : List.of();
        downstreamDependencyIds = downstreamDependencyIds != null
                ? List.copyOf(downstreamDependencyIds) : List.of();
        sliceContent = sliceContent != null ? sliceContent : "";
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
