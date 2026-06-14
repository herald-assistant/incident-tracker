package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseFileCandidate(
        String path,
        GitLabEndpointUseCaseFileRole role,
        int priority,
        List<String> symbols,
        String reason,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabEndpointUseCaseFileCandidate {
        path = GitLabEndpointUseCaseModelSupport.normalizeFilePath(path);
        role = role != null ? role : GitLabEndpointUseCaseFileRole.UNKNOWN;
        priority = GitLabEndpointUseCaseModelSupport.normalizePriority(priority);
        symbols = GitLabEndpointUseCaseModelSupport.copyStrings(symbols);
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
