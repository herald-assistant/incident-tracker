package pl.mkn.tdw.features.flowexplorer.context;

import java.util.List;

public record FlowExplorerContextSnapshot(
        String systemId,
        String systemName,
        String requestedBranch,
        String resolvedRef,
        String gitLabGroup,
        String endpointId,
        String httpMethod,
        String endpointPath,
        FlowExplorerEndpointContext endpoint,
        List<FlowExplorerRepositoryContext> repositories,
        List<FlowExplorerFlowNode> flowNodes,
        List<FlowExplorerFlowRelation> relations,
        List<FlowExplorerSnippetCard> snippetCards,
        List<FlowExplorerOpenApiEndpointContract> openApiEndpointContracts,
        List<String> limitations,
        List<String> suggestedNextReads,
        FlowExplorerContextCoverage coverage
) {

    public FlowExplorerContextSnapshot(
            String systemId,
            String systemName,
            String requestedBranch,
            String resolvedRef,
            String gitLabGroup,
            String endpointId,
            String httpMethod,
            String endpointPath,
            FlowExplorerEndpointContext endpoint,
            List<FlowExplorerRepositoryContext> repositories,
            List<FlowExplorerFlowNode> flowNodes,
            List<FlowExplorerFlowRelation> relations,
            List<FlowExplorerSnippetCard> snippetCards,
            List<String> limitations,
            List<String> suggestedNextReads,
            FlowExplorerContextCoverage coverage
    ) {
        this(
                systemId,
                systemName,
                requestedBranch,
                resolvedRef,
                gitLabGroup,
                endpointId,
                httpMethod,
                endpointPath,
                endpoint,
                repositories,
                flowNodes,
                relations,
                snippetCards,
                List.of(),
                limitations,
                suggestedNextReads,
                coverage
        );
    }

    public FlowExplorerContextSnapshot {
        repositories = repositories != null ? List.copyOf(repositories) : List.of();
        flowNodes = flowNodes != null ? List.copyOf(flowNodes) : List.of();
        relations = relations != null ? List.copyOf(relations) : List.of();
        snippetCards = snippetCards != null ? List.copyOf(snippetCards) : List.of();
        openApiEndpointContracts = openApiEndpointContracts != null ? List.copyOf(openApiEndpointContracts) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
        suggestedNextReads = suggestedNextReads != null ? List.copyOf(suggestedNextReads) : List.of();
    }
}
