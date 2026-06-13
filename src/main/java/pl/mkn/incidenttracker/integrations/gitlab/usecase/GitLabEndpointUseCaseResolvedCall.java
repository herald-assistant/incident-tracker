package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseResolvedCall(
        GitLabEndpointUseCaseMethodCallInfo call,
        GitLabEndpointUseCaseCallResolutionStatus status,
        GitLabEndpointUseCaseResolutionKind resolutionKind,
        String targetType,
        String targetMethodId,
        String targetMethodSignature,
        boolean terminal,
        String terminalReason,
        List<String> candidates
) {
    GitLabEndpointUseCaseResolvedCall {
        status = status != null ? status : GitLabEndpointUseCaseCallResolutionStatus.UNRESOLVED;
        resolutionKind = resolutionKind != null ? resolutionKind : GitLabEndpointUseCaseResolutionKind.UNRESOLVED;
        terminal = terminal || status == GitLabEndpointUseCaseCallResolutionStatus.TERMINAL;
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
    }

    boolean resolved() {
        return status == GitLabEndpointUseCaseCallResolutionStatus.RESOLVED;
    }
}
