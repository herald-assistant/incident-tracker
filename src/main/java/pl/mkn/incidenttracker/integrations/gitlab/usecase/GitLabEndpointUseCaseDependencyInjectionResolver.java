package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
class GitLabEndpointUseCaseDependencyInjectionResolver {

    private static final Set<String> FIELD_INJECTION_ANNOTATIONS = Set.of("Autowired", "Inject", "Resource");
    private static final Set<String> METHOD_INJECTION_ANNOTATIONS = Set.of("Autowired", "Inject");
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"([^\"]*)\"");

    GitLabEndpointUseCaseDependencyResolution resolve(
            GitLabEndpointUseCaseCodeIndex codeIndex,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry
    ) {
        if (codeIndex == null || beanRegistry == null || codeIndex.indexStatus() == GitLabEndpointUseCaseIndexStatus.NOT_BUILT) {
            return GitLabEndpointUseCaseDependencyResolution.from(List.of(), codeIndex != null ? codeIndex.warnings() : List.of());
        }

        var warnings = new ArrayList<GitLabEndpointUseCaseWarning>(beanRegistry.warnings());
        var dependencies = new ArrayList<GitLabEndpointUseCaseResolvedDependency>();

        for (var type : codeIndex.types()) {
            if (!isSpringManagedType(type, beanRegistry)) {
                continue;
            }

            for (var injectionPoint : injectionPoints(type)) {
                dependencies.add(resolveInjectionPoint(injectionPoint, beanRegistry, warnings));
            }
        }

        dependencies.sort(Comparator.comparing(
                        (GitLabEndpointUseCaseResolvedDependency dependency) -> dependency.injectionPoint().declaringType())
                .thenComparing(dependency -> dependency.injectionPoint().memberName())
                .thenComparing(dependency -> dependency.injectionPoint().sourceKind().name()));
        return GitLabEndpointUseCaseDependencyResolution.from(dependencies, warnings);
    }

    private List<GitLabEndpointUseCaseInjectionPoint> injectionPoints(GitLabEndpointUseCaseTypeInfo type) {
        var points = new ArrayList<GitLabEndpointUseCaseInjectionPoint>();
        points.addAll(constructorInjectionPoints(type));
        points.addAll(lombokRequiredArgsConstructorInjectionPoints(type, points));
        points.addAll(recordComponentInjectionPoints(type));
        points.addAll(fieldInjectionPoints(type));
        points.addAll(methodInjectionPoints(type));
        return List.copyOf(points);
    }

    private List<GitLabEndpointUseCaseInjectionPoint> constructorInjectionPoints(GitLabEndpointUseCaseTypeInfo type) {
        var constructors = type.methods().stream()
                .filter(GitLabEndpointUseCaseMethodInfo::constructor)
                .toList();
        var injectableConstructors = constructors.stream()
                .filter(method -> hasAnyAnnotation(method.annotationDetails(), METHOD_INJECTION_ANNOTATIONS))
                .toList();

        if (injectableConstructors.isEmpty() && constructors.size() == 1) {
            injectableConstructors = constructors;
        }

        return injectableConstructors.stream()
                .flatMap(constructor -> constructor.parameters().stream()
                        .map(parameter -> injectionPoint(
                                type,
                                parameter.name(),
                                parameter.type(),
                                qualifier(parameter.annotationDetails()),
                                GitLabEndpointUseCaseInjectionSourceKind.CONSTRUCTOR,
                                parameterLine(parameter, constructor)
                        )))
                .toList();
    }

    private List<GitLabEndpointUseCaseInjectionPoint> lombokRequiredArgsConstructorInjectionPoints(
            GitLabEndpointUseCaseTypeInfo type,
            List<GitLabEndpointUseCaseInjectionPoint> existingPoints
    ) {
        if (!existingPoints.isEmpty() || !hasAnnotation(type.annotationDetails(), "RequiredArgsConstructor")) {
            return List.of();
        }

        return type.fields().stream()
                .filter(field -> !field.staticField())
                .filter(field -> field.finalField() || hasAnnotation(field.annotationDetails(), "NonNull"))
                .map(field -> injectionPoint(
                        type,
                        field.name(),
                        field.type(),
                        qualifier(field.annotationDetails()),
                        GitLabEndpointUseCaseInjectionSourceKind.LOMBOK_REQUIRED_ARGS_CONSTRUCTOR,
                        field.line()
                ))
                .toList();
    }

    private List<GitLabEndpointUseCaseInjectionPoint> recordComponentInjectionPoints(GitLabEndpointUseCaseTypeInfo type) {
        if (type.kind() != GitLabEndpointUseCaseTypeKind.RECORD) {
            return List.of();
        }

        return type.fields().stream()
                .filter(field -> !field.staticField())
                .map(field -> injectionPoint(
                        type,
                        field.name(),
                        field.type(),
                        qualifier(field.annotationDetails()),
                        GitLabEndpointUseCaseInjectionSourceKind.RECORD_COMPONENT,
                        field.line()
                ))
                .toList();
    }

    private List<GitLabEndpointUseCaseInjectionPoint> fieldInjectionPoints(GitLabEndpointUseCaseTypeInfo type) {
        return type.fields().stream()
                .filter(field -> !field.staticField())
                .filter(field -> hasAnyAnnotation(field.annotationDetails(), FIELD_INJECTION_ANNOTATIONS))
                .map(field -> injectionPoint(
                        type,
                        field.name(),
                        field.type(),
                        qualifier(field.annotationDetails()),
                        GitLabEndpointUseCaseInjectionSourceKind.FIELD,
                        field.line()
                ))
                .toList();
    }

    private List<GitLabEndpointUseCaseInjectionPoint> methodInjectionPoints(GitLabEndpointUseCaseTypeInfo type) {
        var points = new ArrayList<GitLabEndpointUseCaseInjectionPoint>();
        for (var method : type.methods()) {
            if (method.constructor() || !hasAnyAnnotation(method.annotationDetails(), METHOD_INJECTION_ANNOTATIONS)) {
                continue;
            }
            for (var parameter : method.parameters()) {
                points.add(injectionPoint(
                        type,
                        parameter.name(),
                        parameter.type(),
                        qualifier(parameter.annotationDetails()),
                        GitLabEndpointUseCaseInjectionSourceKind.METHOD,
                        parameterLine(parameter, method)
                ));
            }
        }
        return List.copyOf(points);
    }

    private GitLabEndpointUseCaseInjectionPoint injectionPoint(
            GitLabEndpointUseCaseTypeInfo type,
            String memberName,
            String requiredType,
            String qualifier,
            GitLabEndpointUseCaseInjectionSourceKind sourceKind,
            Integer line
    ) {
        return new GitLabEndpointUseCaseInjectionPoint(
                type.fqn(),
                memberName,
                normalizeTypeName(requiredType),
                qualifier,
                sourceKind,
                type.sourcePath(),
                line
        );
    }

    private GitLabEndpointUseCaseResolvedDependency resolveInjectionPoint(
            GitLabEndpointUseCaseInjectionPoint injectionPoint,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var candidates = candidatesForType(injectionPoint.requiredType(), beanRegistry);
        if (candidates.isEmpty()) {
            warnings.add(new GitLabEndpointUseCaseWarning(
                    GitLabEndpointUseCaseWarningCodes.DI_BEAN_NOT_FOUND,
                    GitLabEndpointUseCaseWarningSeverity.WARNING,
                    "No Spring bean candidate found for dependency type " + injectionPoint.requiredType() + ".",
                    injectionPoint.sourcePath(),
                    injectionPoint.line(),
                    List.of(injectionPoint.requiredType())
            ));
            return unresolved(injectionPoint, candidates);
        }

        if (StringUtils.hasText(injectionPoint.qualifier())) {
            var qualifiedCandidates = candidates.stream()
                    .filter(candidate -> matchesNameOrQualifier(candidate, injectionPoint.qualifier()))
                    .toList();
            if (qualifiedCandidates.size() == 1) {
                return resolved(injectionPoint, qualifiedCandidates.get(0), candidates);
            }
            if (qualifiedCandidates.isEmpty()) {
                warnings.add(new GitLabEndpointUseCaseWarning(
                        GitLabEndpointUseCaseWarningCodes.DI_QUALIFIER_NOT_FOUND,
                        GitLabEndpointUseCaseWarningSeverity.WARNING,
                        "No Spring bean candidate matched qualifier '" + injectionPoint.qualifier() + "'.",
                        injectionPoint.sourcePath(),
                        injectionPoint.line(),
                        candidateNames(candidates)
                ));
                return unresolved(injectionPoint, candidates);
            }
            return ambiguous(injectionPoint, qualifiedCandidates, warnings);
        }

        if (candidates.size() == 1) {
            return resolved(injectionPoint, candidates.get(0), candidates);
        }

        var primaryCandidates = candidates.stream()
                .filter(GitLabEndpointUseCaseSpringBean::primary)
                .toList();
        if (primaryCandidates.size() == 1) {
            return resolved(injectionPoint, primaryCandidates.get(0), candidates);
        }

        var nameMatchedCandidates = candidates.stream()
                .filter(candidate -> matchesNameOrQualifier(candidate, injectionPoint.memberName()))
                .toList();
        if (nameMatchedCandidates.size() == 1) {
            return resolved(injectionPoint, nameMatchedCandidates.get(0), candidates);
        }

        return ambiguous(injectionPoint, candidates, warnings);
    }

    private GitLabEndpointUseCaseResolvedDependency resolved(
            GitLabEndpointUseCaseInjectionPoint injectionPoint,
            GitLabEndpointUseCaseSpringBean resolvedBean,
            List<GitLabEndpointUseCaseSpringBean> candidates
    ) {
        return new GitLabEndpointUseCaseResolvedDependency(
                injectionPoint,
                GitLabEndpointUseCaseDependencyResolutionStatus.RESOLVED,
                resolvedBean,
                candidates,
                resolutionKind(injectionPoint.requiredType(), resolvedBean)
        );
    }

    private GitLabEndpointUseCaseResolvedDependency unresolved(
            GitLabEndpointUseCaseInjectionPoint injectionPoint,
            List<GitLabEndpointUseCaseSpringBean> candidates
    ) {
        return new GitLabEndpointUseCaseResolvedDependency(
                injectionPoint,
                GitLabEndpointUseCaseDependencyResolutionStatus.UNRESOLVED,
                null,
                candidates,
                GitLabEndpointUseCaseResolutionKind.UNRESOLVED
        );
    }

    private GitLabEndpointUseCaseResolvedDependency ambiguous(
            GitLabEndpointUseCaseInjectionPoint injectionPoint,
            List<GitLabEndpointUseCaseSpringBean> candidates,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        warnings.add(new GitLabEndpointUseCaseWarning(
                GitLabEndpointUseCaseWarningCodes.DI_BEAN_AMBIGUOUS,
                GitLabEndpointUseCaseWarningSeverity.WARNING,
                "Multiple Spring bean candidates found for dependency type " + injectionPoint.requiredType() + ".",
                injectionPoint.sourcePath(),
                injectionPoint.line(),
                candidateNames(candidates)
        ));
        return new GitLabEndpointUseCaseResolvedDependency(
                injectionPoint,
                GitLabEndpointUseCaseDependencyResolutionStatus.AMBIGUOUS,
                null,
                candidates,
                GitLabEndpointUseCaseResolutionKind.UNRESOLVED
        );
    }

    private List<GitLabEndpointUseCaseSpringBean> candidatesForType(
            String requiredType,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry
    ) {
        var candidates = new LinkedHashSet<GitLabEndpointUseCaseSpringBean>();
        var normalizedType = normalizeTypeName(requiredType);
        if (StringUtils.hasText(normalizedType)) {
            candidates.addAll(beanRegistry.candidatesForType(normalizedType));
            candidates.addAll(beanRegistry.candidatesForType(simpleName(normalizedType)));
        }
        return List.copyOf(candidates);
    }

    private GitLabEndpointUseCaseResolutionKind resolutionKind(
            String requiredType,
            GitLabEndpointUseCaseSpringBean resolvedBean
    ) {
        var normalizedType = normalizeTypeName(requiredType);
        if (normalizedType == null || resolvedBean == null) {
            return GitLabEndpointUseCaseResolutionKind.UNRESOLVED;
        }
        if (normalizedType.equals(resolvedBean.type()) || simpleName(normalizedType).equals(simpleName(resolvedBean.type()))) {
            return GitLabEndpointUseCaseResolutionKind.SPRING_BEAN;
        }
        return GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC;
    }

    private boolean isSpringManagedType(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry
    ) {
        return beanRegistry.beans().stream()
                .anyMatch(bean -> bean.factoryMethodId() == null && type.fqn().equals(bean.type()));
    }

    private boolean matchesNameOrQualifier(GitLabEndpointUseCaseSpringBean candidate, String nameOrQualifier) {
        if (!StringUtils.hasText(nameOrQualifier)) {
            return false;
        }
        return nameOrQualifier.equals(candidate.beanName())
                || candidate.aliases().contains(nameOrQualifier)
                || candidate.qualifiers().contains(nameOrQualifier);
    }

    private List<String> candidateNames(List<GitLabEndpointUseCaseSpringBean> candidates) {
        return candidates.stream()
                .map(bean -> bean.beanName() + ":" + bean.type())
                .limit(20)
                .toList();
    }

    private String qualifier(List<GitLabEndpointUseCaseAnnotationInfo> annotations) {
        var qualifier = firstStringLiteral(annotations, "Qualifier");
        if (StringUtils.hasText(qualifier)) {
            return qualifier;
        }
        var named = firstStringLiteral(annotations, "Named");
        if (StringUtils.hasText(named)) {
            return named;
        }
        return firstStringLiteral(annotations, "Resource");
    }

    private String firstStringLiteral(List<GitLabEndpointUseCaseAnnotationInfo> annotations, String annotationName) {
        for (var annotation : annotations != null ? annotations : List.<GitLabEndpointUseCaseAnnotationInfo>of()) {
            if (annotationName.equals(simpleName(annotation.name()))) {
                var literals = stringLiterals(annotation.expression());
                if (!literals.isEmpty()) {
                    return literals.get(0);
                }
            }
        }
        return null;
    }

    private List<String> stringLiterals(String expression) {
        var values = new ArrayList<String>();
        var matcher = STRING_LITERAL_PATTERN.matcher(expression != null ? expression : "");
        while (matcher.find()) {
            if (StringUtils.hasText(matcher.group(1))) {
                values.add(matcher.group(1).trim());
            }
        }
        return List.copyOf(values);
    }

    private boolean hasAnnotation(List<GitLabEndpointUseCaseAnnotationInfo> annotations, String annotationName) {
        return annotations != null && annotations.stream()
                .anyMatch(annotation -> annotationName.equals(simpleName(annotation.name())));
    }

    private boolean hasAnyAnnotation(
            List<GitLabEndpointUseCaseAnnotationInfo> annotations,
            Set<String> annotationNames
    ) {
        return annotations != null && annotations.stream()
                .map(GitLabEndpointUseCaseAnnotationInfo::name)
                .map(this::simpleName)
                .anyMatch(annotationNames::contains);
    }

    private Integer parameterLine(
            GitLabEndpointUseCaseParameterInfo parameter,
            GitLabEndpointUseCaseMethodInfo method
    ) {
        return parameter.annotationDetails().stream()
                .map(GitLabEndpointUseCaseAnnotationInfo::line)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(method.lineStart());
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
