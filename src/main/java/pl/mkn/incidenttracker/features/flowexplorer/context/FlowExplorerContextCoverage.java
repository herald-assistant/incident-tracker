package pl.mkn.incidenttracker.features.flowexplorer.context;

public record FlowExplorerContextCoverage(
        boolean endpointResolved,
        int repositoryRefCount,
        int attemptedRepositoryCount,
        int flowNodeCount,
        int methodCount,
        int relationCount,
        int snippetCardCount,
        int snippetCharacterCount,
        boolean snippetBudgetReached,
        int unresolvedReferenceCount,
        int limitationCount,
        boolean maxDepthReached,
        boolean maxFilesReached,
        boolean readFileLimitReached,
        String confidence
) {
}
