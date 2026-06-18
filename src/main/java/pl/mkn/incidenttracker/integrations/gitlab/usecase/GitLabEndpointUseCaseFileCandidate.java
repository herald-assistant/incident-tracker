package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.LinkedHashSet;
import java.util.List;

public record GitLabEndpointUseCaseFileCandidate(
        String path,
        GitLabEndpointUseCaseFileRole role,
        int priority,
        List<String> symbols,
        List<GitLabEndpointUseCaseMethodCandidate> methods,
        String reason,
        GitLabEndpointUseCaseConfidence confidence
) {
    public GitLabEndpointUseCaseFileCandidate(
            String path,
            GitLabEndpointUseCaseFileRole role,
            int priority,
            List<String> symbols,
            String reason,
            GitLabEndpointUseCaseConfidence confidence
    ) {
        this(path, role, priority, symbols, List.of(), reason, confidence);
    }

    public GitLabEndpointUseCaseFileCandidate {
        path = GitLabEndpointUseCaseModelSupport.normalizeFilePath(path);
        role = role != null ? role : GitLabEndpointUseCaseFileRole.UNKNOWN;
        priority = GitLabEndpointUseCaseModelSupport.normalizePriority(priority);
        methods = GitLabEndpointUseCaseModelSupport.copy(methods);
        var mergedSymbols = new LinkedHashSet<>(GitLabEndpointUseCaseModelSupport.copyStrings(symbols));
        methods.stream()
                .map(GitLabEndpointUseCaseMethodCandidate::methodName)
                .forEach(symbol -> {
                    var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(symbol);
                    if (normalized != null) {
                        mergedSymbols.add(normalized);
                    }
                });
        symbols = List.copyOf(mergedSymbols);
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
        confidence = confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW;
    }
}
