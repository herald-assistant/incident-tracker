package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseParameterInfo(
        String name,
        String type,
        List<String> annotations,
        List<GitLabEndpointUseCaseAnnotationInfo> annotationDetails
) {
    GitLabEndpointUseCaseParameterInfo {
        annotations = annotations != null ? List.copyOf(annotations) : List.of();
        annotationDetails = annotationDetails != null ? List.copyOf(annotationDetails) : List.of();
    }
}
