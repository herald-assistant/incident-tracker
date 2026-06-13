package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabEndpointUseCaseClassItem(
        String classFqn,
        GitLabEndpointUseCaseRole role,
        int depth,
        List<String> methods,
        boolean terminal,
        String reason
) {
    public GitLabEndpointUseCaseClassItem {
        role = role != null ? role : GitLabEndpointUseCaseRole.UNKNOWN;
        methods = methods != null ? List.copyOf(methods) : List.of();
    }
}
