package pl.mkn.tdw.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseUnresolvedReference(
        String symbol,
        String ownerPath,
        String reason,
        List<String> searchedKeywords,
        List<String> candidates
) {
    public GitLabEndpointUseCaseUnresolvedReference {
        symbol = GitLabEndpointUseCaseModelSupport.trimToNull(symbol);
        ownerPath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(ownerPath);
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
        searchedKeywords = GitLabEndpointUseCaseModelSupport.copyStrings(searchedKeywords);
        candidates = GitLabEndpointUseCaseModelSupport.copyStrings(candidates);
    }
}
