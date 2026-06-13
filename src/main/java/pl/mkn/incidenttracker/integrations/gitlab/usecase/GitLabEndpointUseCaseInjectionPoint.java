package pl.mkn.incidenttracker.integrations.gitlab.usecase;

record GitLabEndpointUseCaseInjectionPoint(
        String declaringType,
        String memberName,
        String requiredType,
        String qualifier,
        GitLabEndpointUseCaseInjectionSourceKind sourceKind,
        String sourcePath,
        Integer line
) {
}
