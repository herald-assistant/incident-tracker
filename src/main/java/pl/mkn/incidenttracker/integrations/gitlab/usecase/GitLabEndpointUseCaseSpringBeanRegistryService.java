package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
class GitLabEndpointUseCaseSpringBeanRegistryService {

    private static final Set<String> COMPONENT_STEREOTYPES = Set.of(
            "Component",
            "Service",
            "Repository",
            "Controller",
            "RestController",
            "Configuration"
    );
    private static final Set<String> SPRING_DATA_REPOSITORY_TYPES = Set.of(
            "Repository",
            "CrudRepository",
            "ListCrudRepository",
            "PagingAndSortingRepository",
            "ListPagingAndSortingRepository",
            "JpaRepository",
            "MongoRepository",
            "ReactiveCrudRepository",
            "R2dbcRepository"
    );
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"([^\"]*)\"");

    GitLabEndpointUseCaseSpringBeanRegistry buildRegistry(GitLabEndpointUseCaseCodeIndex codeIndex) {
        if (codeIndex == null || codeIndex.indexStatus() == GitLabEndpointUseCaseIndexStatus.NOT_BUILT) {
            return new GitLabEndpointUseCaseSpringBeanRegistry(List.of(), Map.of(), Map.of(),
                    codeIndex != null ? codeIndex.warnings() : List.of());
        }

        var beans = new ArrayList<GitLabEndpointUseCaseSpringBean>();
        var warnings = new ArrayList<>(codeIndex.warnings());
        var unresolvedWarnings = new LinkedHashSet<String>();

        for (var type : codeIndex.types()) {
            componentBean(type, codeIndex, warnings, unresolvedWarnings).ifPresent(beans::add);
            beans.addAll(beanMethodBeans(type, codeIndex, warnings, unresolvedWarnings));
        }

        beans.sort(Comparator.comparing(GitLabEndpointUseCaseSpringBean::beanName)
                .thenComparing(GitLabEndpointUseCaseSpringBean::type));

        return new GitLabEndpointUseCaseSpringBeanRegistry(
                beans,
                beansByAssignableType(beans),
                beansByName(beans),
                warnings
        );
    }

    private java.util.Optional<GitLabEndpointUseCaseSpringBean> componentBean(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseCodeIndex codeIndex,
            List<GitLabEndpointUseCaseWarning> warnings,
            LinkedHashSet<String> unresolvedWarnings
    ) {
        var stereotypes = stereotypes(type);
        var mapper = isMapStructSpringMapper(type);
        var feignClient = hasAnnotation(type.annotationDetails(), "FeignClient");
        var springDataRepository = isSpringDataRepository(type);

        if (stereotypes.isEmpty() && !mapper && !feignClient && !springDataRepository) {
            return java.util.Optional.empty();
        }

        var beanNames = beanNames(type.annotationDetails());
        var beanName = beanNames.isEmpty() ? defaultBeanName(type.simpleName()) : beanNames.get(0);
        var aliases = beanNames.size() > 1 ? beanNames.subList(1, beanNames.size()) : List.<String>of();
        var sourceKind = sourceKind(type, mapper, feignClient, springDataRepository);

        return java.util.Optional.of(new GitLabEndpointUseCaseSpringBean(
                beanName,
                aliases,
                type.fqn(),
                type.fqn(),
                null,
                null,
                sourceKind,
                stereotypes,
                qualifiers(type.annotationDetails()),
                hasAnnotation(type.annotationDetails(), "Primary"),
                assignableTypes(type, codeIndex, warnings, unresolvedWarnings),
                type.sourcePath(),
                type.lineStart(),
                type.lineEnd()
        ));
    }

    private List<GitLabEndpointUseCaseSpringBean> beanMethodBeans(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseCodeIndex codeIndex,
            List<GitLabEndpointUseCaseWarning> warnings,
            LinkedHashSet<String> unresolvedWarnings
    ) {
        var beans = new ArrayList<GitLabEndpointUseCaseSpringBean>();
        var configurationClass = hasAnnotation(type.annotationDetails(), "Configuration");

        for (var method : type.methods()) {
            if (method.constructor() || !hasAnnotation(method.annotationDetails(), "Bean")) {
                continue;
            }

            var beanAnnotation = annotation(method.annotationDetails(), "Bean");
            var beanNames = beanAnnotation != null ? stringLiterals(beanAnnotation.expression()) : List.<String>of();
            var beanName = beanNames.isEmpty() ? method.name() : beanNames.get(0);
            var aliases = beanNames.size() > 1 ? beanNames.subList(1, beanNames.size()) : List.<String>of();
            var returnType = normalizeTypeName(method.returnType());
            if (!StringUtils.hasText(returnType)) {
                continue;
            }

            var stereotypes = new ArrayList<String>();
            if (configurationClass) {
                stereotypes.add("Configuration");
            }
            stereotypes.add("Bean");

            beans.add(new GitLabEndpointUseCaseSpringBean(
                    beanName,
                    aliases,
                    returnType,
                    type.fqn(),
                    method.id(),
                    method.name(),
                    GitLabEndpointUseCaseSpringBeanSourceKind.BEAN_METHOD,
                    stereotypes,
                    qualifiers(method.annotationDetails()),
                    hasAnnotation(method.annotationDetails(), "Primary"),
                    assignableTypes(returnType, codeIndex, warnings, unresolvedWarnings, type.sourcePath(), method.lineStart()),
                    type.sourcePath(),
                    method.lineStart(),
                    method.lineEnd()
            ));
        }

        return List.copyOf(beans);
    }

    private GitLabEndpointUseCaseSpringBeanSourceKind sourceKind(
            GitLabEndpointUseCaseTypeInfo type,
            boolean mapper,
            boolean feignClient,
            boolean springDataRepository
    ) {
        if (mapper) {
            return GitLabEndpointUseCaseSpringBeanSourceKind.MAPSTRUCT_MAPPER;
        }
        if (feignClient) {
            return GitLabEndpointUseCaseSpringBeanSourceKind.FEIGN_CLIENT;
        }
        if (springDataRepository) {
            return GitLabEndpointUseCaseSpringBeanSourceKind.SPRING_DATA_REPOSITORY;
        }
        if (hasAnnotation(type.annotationDetails(), "Configuration")) {
            return GitLabEndpointUseCaseSpringBeanSourceKind.CONFIGURATION_CLASS;
        }
        return GitLabEndpointUseCaseSpringBeanSourceKind.COMPONENT;
    }

    private List<String> assignableTypes(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseCodeIndex codeIndex,
            List<GitLabEndpointUseCaseWarning> warnings,
            LinkedHashSet<String> unresolvedWarnings
    ) {
        return assignableTypes(type.fqn(), codeIndex, warnings, unresolvedWarnings, type.sourcePath(), type.lineStart());
    }

    private List<String> assignableTypes(
            String typeName,
            GitLabEndpointUseCaseCodeIndex codeIndex,
            List<GitLabEndpointUseCaseWarning> warnings,
            LinkedHashSet<String> unresolvedWarnings,
            String sourcePath,
            Integer line
    ) {
        var assignableTypes = new LinkedHashSet<String>();
        var queue = new ArrayDeque<String>();
        addTypeAliases(assignableTypes, typeName, codeIndex, warnings, unresolvedWarnings, sourcePath, line);
        queue.add(normalizeTypeName(typeName));

        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            var currentType = resolveType(current, codeIndex);
            if (currentType == null) {
                continue;
            }

            for (var parent : currentType.directParentTypes()) {
                var normalizedParent = normalizeTypeName(parent);
                if (!StringUtils.hasText(normalizedParent)) {
                    continue;
                }
                var beforeSize = assignableTypes.size();
                addTypeAliases(assignableTypes, normalizedParent, codeIndex, warnings, unresolvedWarnings,
                        currentType.sourcePath(), currentType.lineStart());
                if (assignableTypes.size() > beforeSize) {
                    queue.add(normalizedParent);
                }
            }
        }

        return List.copyOf(assignableTypes);
    }

    private void addTypeAliases(
            LinkedHashSet<String> assignableTypes,
            String rawTypeName,
            GitLabEndpointUseCaseCodeIndex codeIndex,
            List<GitLabEndpointUseCaseWarning> warnings,
            LinkedHashSet<String> unresolvedWarnings,
            String sourcePath,
            Integer line
    ) {
        var typeName = normalizeTypeName(rawTypeName);
        if (!StringUtils.hasText(typeName)) {
            return;
        }

        assignableTypes.add(typeName);
        assignableTypes.add(simpleName(typeName));

        var resolvedType = resolveType(typeName, codeIndex);
        if (resolvedType != null) {
            assignableTypes.add(resolvedType.fqn());
            assignableTypes.add(resolvedType.simpleName());
            return;
        }

        if (shouldWarnUnresolvedAssignableType(typeName) && unresolvedWarnings.add(sourcePath + "|" + typeName)) {
            warnings.add(new GitLabEndpointUseCaseWarning(
                    GitLabEndpointUseCaseWarningCodes.BEAN_ASSIGNABLE_TYPE_UNRESOLVED,
                    GitLabEndpointUseCaseWarningSeverity.INFO,
                    "Assignable parent type is outside the indexed source snapshot: " + typeName + ".",
                    sourcePath,
                    line,
                    List.of(typeName)
            ));
        }
    }

    private GitLabEndpointUseCaseTypeInfo resolveType(String typeName, GitLabEndpointUseCaseCodeIndex codeIndex) {
        var normalizedTypeName = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalizedTypeName)) {
            return null;
        }
        var byFqn = codeIndex.typesByFqn().get(normalizedTypeName);
        if (byFqn != null) {
            return byFqn;
        }
        var bySimpleName = codeIndex.typesBySimpleName().get(simpleName(normalizedTypeName));
        return bySimpleName != null && bySimpleName.size() == 1 ? bySimpleName.get(0) : null;
    }

    private boolean shouldWarnUnresolvedAssignableType(String typeName) {
        var simpleName = simpleName(typeName);
        return !typeName.startsWith("java.")
                && !typeName.startsWith("jakarta.")
                && !typeName.startsWith("org.springframework.")
                && !SPRING_DATA_REPOSITORY_TYPES.contains(simpleName)
                && !"Object".equals(simpleName)
                && !"Serializable".equals(simpleName);
    }

    private Map<String, List<GitLabEndpointUseCaseSpringBean>> beansByAssignableType(
            List<GitLabEndpointUseCaseSpringBean> beans
    ) {
        var byType = new LinkedHashMap<String, ArrayList<GitLabEndpointUseCaseSpringBean>>();
        for (var bean : beans) {
            for (var assignableType : bean.assignableTypes()) {
                byType.computeIfAbsent(assignableType, ignored -> new ArrayList<>()).add(bean);
            }
        }

        var copy = new LinkedHashMap<String, List<GitLabEndpointUseCaseSpringBean>>();
        byType.forEach((type, matchedBeans) -> copy.put(type, List.copyOf(matchedBeans)));
        return copy;
    }

    private Map<String, GitLabEndpointUseCaseSpringBean> beansByName(List<GitLabEndpointUseCaseSpringBean> beans) {
        var byName = new LinkedHashMap<String, GitLabEndpointUseCaseSpringBean>();
        for (var bean : beans) {
            byName.putIfAbsent(bean.beanName(), bean);
            for (var alias : bean.aliases()) {
                byName.putIfAbsent(alias, bean);
            }
        }
        return byName;
    }

    private boolean isSpringDataRepository(GitLabEndpointUseCaseTypeInfo type) {
        return type.kind() == GitLabEndpointUseCaseTypeKind.INTERFACE
                && type.directParentTypes().stream()
                .map(this::normalizeTypeName)
                .map(this::simpleName)
                .anyMatch(SPRING_DATA_REPOSITORY_TYPES::contains);
    }

    private boolean isMapStructSpringMapper(GitLabEndpointUseCaseTypeInfo type) {
        var mapperAnnotation = annotation(type.annotationDetails(), "Mapper");
        if (mapperAnnotation == null) {
            return false;
        }
        var expression = mapperAnnotation.expression() != null ? mapperAnnotation.expression() : "";
        return expression.contains("\"spring\"")
                || expression.contains(".SPRING")
                || expression.contains("ComponentModel.SPRING");
    }

    private List<String> stereotypes(GitLabEndpointUseCaseTypeInfo type) {
        return type.annotations().stream()
                .map(this::simpleName)
                .filter(COMPONENT_STEREOTYPES::contains)
                .toList();
    }

    private List<String> qualifiers(List<GitLabEndpointUseCaseAnnotationInfo> annotations) {
        return annotations != null
                ? annotations.stream()
                .filter(annotation -> "Qualifier".equals(simpleName(annotation.name())))
                .flatMap(annotation -> stringLiterals(annotation.expression()).stream())
                .filter(StringUtils::hasText)
                .distinct()
                .toList()
                : List.of();
    }

    private List<String> beanNames(List<GitLabEndpointUseCaseAnnotationInfo> annotations) {
        var names = new LinkedHashSet<String>();
        for (var annotation : annotations != null ? annotations : List.<GitLabEndpointUseCaseAnnotationInfo>of()) {
            var annotationName = simpleName(annotation.name());
            if (COMPONENT_STEREOTYPES.contains(annotationName) || "FeignClient".equals(annotationName)) {
                names.addAll(stringLiterals(annotation.expression()));
            }
        }
        return names.stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    private boolean hasAnnotation(List<GitLabEndpointUseCaseAnnotationInfo> annotations, String annotationName) {
        return annotation(annotations, annotationName) != null;
    }

    private GitLabEndpointUseCaseAnnotationInfo annotation(
            List<GitLabEndpointUseCaseAnnotationInfo> annotations,
            String annotationName
    ) {
        for (var annotation : annotations != null ? annotations : List.<GitLabEndpointUseCaseAnnotationInfo>of()) {
            if (annotationName.equals(simpleName(annotation.name()))) {
                return annotation;
            }
        }
        return null;
    }

    private List<String> stringLiterals(String expression) {
        var values = new LinkedHashSet<String>();
        var matcher = STRING_LITERAL_PATTERN.matcher(expression != null ? expression : "");
        while (matcher.find()) {
            var value = matcher.group(1);
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        }
        return List.copyOf(values);
    }

    private String defaultBeanName(String simpleName) {
        if (!StringUtils.hasText(simpleName)) {
            return "";
        }
        if (simpleName.length() > 1
                && Character.isUpperCase(simpleName.charAt(0))
                && Character.isUpperCase(simpleName.charAt(1))) {
            return simpleName;
        }
        return simpleName.substring(0, 1).toLowerCase(Locale.ROOT) + simpleName.substring(1);
    }

    private String normalizeTypeName(String typeName) {
        if (!StringUtils.hasText(typeName)) {
            return null;
        }
        var normalized = typeName.trim();
        var genericIndex = normalized.indexOf('<');
        if (genericIndex >= 0) {
            normalized = normalized.substring(0, genericIndex);
        }
        while (normalized.endsWith("[]")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("...")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return StringUtils.hasText(normalized) ? normalized.trim() : null;
    }

    private String simpleName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var normalized = normalizeTypeName(value);
        var dotIndex = normalized.lastIndexOf('.');
        return dotIndex >= 0 ? normalized.substring(dotIndex + 1) : normalized;
    }
}
