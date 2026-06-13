package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record GitLabEndpointUseCaseSpringBeanRegistry(
        List<GitLabEndpointUseCaseSpringBean> beans,
        Map<String, List<GitLabEndpointUseCaseSpringBean>> beansByAssignableType,
        Map<String, GitLabEndpointUseCaseSpringBean> beansByName,
        List<GitLabEndpointUseCaseWarning> warnings
) {
    GitLabEndpointUseCaseSpringBeanRegistry {
        beans = beans != null ? List.copyOf(beans) : List.of();
        beansByAssignableType = copyListMap(beansByAssignableType);
        beansByName = copyBeanMap(beansByName);
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    List<GitLabEndpointUseCaseSpringBean> candidatesForType(String typeName) {
        return beansByAssignableType.getOrDefault(typeName, List.of());
    }

    private static Map<String, List<GitLabEndpointUseCaseSpringBean>> copyListMap(
            Map<String, List<GitLabEndpointUseCaseSpringBean>> source
    ) {
        var copy = new LinkedHashMap<String, List<GitLabEndpointUseCaseSpringBean>>();
        if (source != null) {
            source.forEach((key, value) -> copy.put(key, value != null ? List.copyOf(value) : List.of()));
        }
        return Map.copyOf(copy);
    }

    private static Map<String, GitLabEndpointUseCaseSpringBean> copyBeanMap(
            Map<String, GitLabEndpointUseCaseSpringBean> source
    ) {
        var copy = new LinkedHashMap<String, GitLabEndpointUseCaseSpringBean>();
        if (source != null) {
            copy.putAll(source);
        }
        return Map.copyOf(copy);
    }
}
