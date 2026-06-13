package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseTypeInfo(
        String fqn,
        String packageName,
        String simpleName,
        GitLabEndpointUseCaseTypeKind kind,
        String sourcePath,
        Integer lineStart,
        Integer lineEnd,
        List<String> annotations,
        List<GitLabEndpointUseCaseAnnotationInfo> annotationDetails,
        List<String> modifiers,
        List<String> extendsTypes,
        List<String> implementsTypes,
        List<GitLabEndpointUseCaseFieldInfo> fields,
        List<GitLabEndpointUseCaseMethodInfo> methods
) {
    GitLabEndpointUseCaseTypeInfo {
        annotations = annotations != null ? List.copyOf(annotations) : List.of();
        annotationDetails = annotationDetails != null ? List.copyOf(annotationDetails) : List.of();
        modifiers = modifiers != null ? List.copyOf(modifiers) : List.of();
        extendsTypes = extendsTypes != null ? List.copyOf(extendsTypes) : List.of();
        implementsTypes = implementsTypes != null ? List.copyOf(implementsTypes) : List.of();
        fields = fields != null ? List.copyOf(fields) : List.of();
        methods = methods != null ? List.copyOf(methods) : List.of();
    }

    List<String> directParentTypes() {
        var parents = new java.util.ArrayList<String>();
        parents.addAll(extendsTypes);
        parents.addAll(implementsTypes);
        return List.copyOf(parents);
    }
}
