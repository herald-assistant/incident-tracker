package pl.mkn.incidenttracker.integrations.gitlab.usecase;

record GitLabEndpointUseCaseLocalVariableInfo(
        String name,
        String type,
        Integer line
) {
}
