package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseWarning(
        String code,
        GitLabEndpointUseCaseWarningSeverity severity,
        String message,
        String sourcePath,
        Integer line,
        List<String> candidates
) {
    public GitLabEndpointUseCaseWarning {
        severity = severity != null ? severity : GitLabEndpointUseCaseWarningSeverity.WARNING;
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
    }
}
