package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseMethodInfo(
        String id,
        String name,
        String signature,
        String returnType,
        List<GitLabEndpointUseCaseParameterInfo> parameters,
        List<String> annotations,
        List<GitLabEndpointUseCaseAnnotationInfo> annotationDetails,
        List<String> modifiers,
        List<GitLabEndpointUseCaseLocalVariableInfo> localVariables,
        boolean constructor,
        Integer lineStart,
        Integer lineEnd
) {
    GitLabEndpointUseCaseMethodInfo {
        parameters = parameters != null ? List.copyOf(parameters) : List.of();
        annotations = annotations != null ? List.copyOf(annotations) : List.of();
        annotationDetails = annotationDetails != null ? List.copyOf(annotationDetails) : List.of();
        modifiers = modifiers != null ? List.copyOf(modifiers) : List.of();
        localVariables = localVariables != null ? List.copyOf(localVariables) : List.of();
    }
}
