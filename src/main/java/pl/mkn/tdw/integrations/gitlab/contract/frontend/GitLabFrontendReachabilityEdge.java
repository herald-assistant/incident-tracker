package pl.mkn.tdw.integrations.gitlab.contract.frontend;

public record GitLabFrontendReachabilityEdge(
        String fromId,
        String toId,
        GitLabFrontendReachabilityEdgeKind kind,
        String label,
        String sourcePath,
        String sourceSymbol,
        String memberSymbol
) {
}
