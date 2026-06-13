package pl.mkn.incidenttracker.integrations.gitlab.usecase;

record GitLabEndpointUseCaseAnnotationInfo(
        String name,
        String expression,
        Integer line
) {
}
