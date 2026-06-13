package pl.mkn.incidenttracker.integrations.gitlab.usecase;

record GitLabEndpointUseCaseMethodCallInfo(
        String callerMethodId,
        String sourcePath,
        Integer line,
        String receiver,
        String name,
        int argumentCount,
        boolean constructorCall,
        String expression
) {
}
