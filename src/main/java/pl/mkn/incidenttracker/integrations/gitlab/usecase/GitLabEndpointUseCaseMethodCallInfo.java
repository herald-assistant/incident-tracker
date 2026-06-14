package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseMethodCallInfo(
        String callerMethodId,
        String sourcePath,
        Integer line,
        String receiver,
        String name,
        int argumentCount,
        List<String> arguments,
        boolean constructorCall,
        String expression
) {
    GitLabEndpointUseCaseMethodCallInfo {
        arguments = arguments != null ? List.copyOf(arguments) : List.of();
    }
}
