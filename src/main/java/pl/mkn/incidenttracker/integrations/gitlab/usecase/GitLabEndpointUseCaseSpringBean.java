package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.List;

record GitLabEndpointUseCaseSpringBean(
        String beanName,
        List<String> aliases,
        String type,
        String declaringType,
        String factoryMethodId,
        String factoryMethodName,
        GitLabEndpointUseCaseSpringBeanSourceKind sourceKind,
        List<String> stereotypes,
        List<String> qualifiers,
        boolean primary,
        List<String> assignableTypes,
        String sourcePath,
        Integer lineStart,
        Integer lineEnd
) {
    GitLabEndpointUseCaseSpringBean {
        aliases = aliases != null ? List.copyOf(aliases) : List.of();
        stereotypes = stereotypes != null ? List.copyOf(stereotypes) : List.of();
        qualifiers = qualifiers != null ? List.copyOf(qualifiers) : List.of();
        assignableTypes = assignableTypes != null ? List.copyOf(assignableTypes) : List.of();
    }
}
