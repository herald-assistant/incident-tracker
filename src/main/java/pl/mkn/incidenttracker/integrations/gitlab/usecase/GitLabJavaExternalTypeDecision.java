package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

public record GitLabJavaExternalTypeDecision(
        String requestedName,
        String qualifiedName,
        GitLabJavaExternalTypeClassification classification,
        String signal,
        String reason,
        List<String> matchedImports
) {
    public GitLabJavaExternalTypeDecision {
        requestedName = GitLabEndpointUseCaseModelSupport.trimToNull(requestedName);
        qualifiedName = GitLabEndpointUseCaseModelSupport.trimToNull(qualifiedName);
        classification = classification != null
                ? classification
                : GitLabJavaExternalTypeClassification.LOCAL_LOOKUP_FIRST;
        signal = GitLabEndpointUseCaseModelSupport.trimToNull(signal);
        reason = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
        matchedImports = GitLabEndpointUseCaseModelSupport.copyStrings(matchedImports);
    }

    public boolean sourceLookupAllowed() {
        return classification.sourceLookupAllowed();
    }
}
