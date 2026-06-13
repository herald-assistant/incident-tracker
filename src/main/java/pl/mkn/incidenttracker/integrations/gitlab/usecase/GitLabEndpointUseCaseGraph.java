package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseGraph(
        List<GitLabEndpointUseCaseNode> nodes,
        List<GitLabEndpointUseCaseEdge> edges
) {
    public GitLabEndpointUseCaseGraph {
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        edges = edges != null ? List.copyOf(edges) : List.of();
    }

    public static GitLabEndpointUseCaseGraph empty() {
        return new GitLabEndpointUseCaseGraph(List.of(), List.of());
    }
}
