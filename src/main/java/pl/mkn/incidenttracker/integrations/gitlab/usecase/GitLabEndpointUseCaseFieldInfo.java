package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseFieldInfo(
        String name,
        String type,
        List<String> annotations,
        List<GitLabEndpointUseCaseAnnotationInfo> annotationDetails,
        List<String> modifiers,
        boolean staticField,
        boolean finalField,
        Integer line
) {
    GitLabEndpointUseCaseFieldInfo {
        annotations = annotations != null ? List.copyOf(annotations) : List.of();
        annotationDetails = annotationDetails != null ? List.copyOf(annotationDetails) : List.of();
        modifiers = modifiers != null ? List.copyOf(modifiers) : List.of();
    }
}
