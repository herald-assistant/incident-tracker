package pl.mkn.tdw.integrations.gitlab.usecase;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@Component
public class GitLabJavaMethodLocator {

    public GitLabJavaMethodResolution resolveMethod(
            GitLabJavaAstFile astFile,
            String declaringTypeName,
            String methodName
    ) {
        return resolveMethod(astFile, declaringTypeName, methodName, null);
    }

    public GitLabJavaMethodResolution resolveMethod(
            GitLabJavaAstFile astFile,
            String declaringTypeName,
            String methodName,
            Integer argumentCount
    ) {
        return resolveMethod(astFile, declaringTypeName, methodName, argumentCount, List.of());
    }

    public GitLabJavaMethodResolution resolveMethod(
            GitLabJavaAstFile astFile,
            String declaringTypeName,
            String methodName,
            Integer argumentCount,
            List<String> expectedParameterTypes
    ) {
        var normalizedMethodName = GitLabEndpointUseCaseModelSupport.trimToNull(methodName);
        var normalizedExpectedParameterTypes = normalizeParameterTypes(expectedParameterTypes);
        if (!StringUtils.hasText(normalizedMethodName)) {
            return new GitLabJavaMethodResolution(
                    GitLabJavaMethodResolutionStatus.INVALID_REQUEST,
                    null,
                    List.of(),
                    List.of("Method name is required."),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }
        if (astFile == null || !astFile.parsed()) {
            return new GitLabJavaMethodResolution(
                    GitLabJavaMethodResolutionStatus.PARSE_FAILED,
                    null,
                    List.of(),
                    astFile != null ? astFile.limitations() : List.of("Context Java source was not parsed."),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }

        var methodCandidates = astFile.compilationUnit().findAll(MethodDeclaration.class).stream()
                .filter(method -> normalizedMethodName.equals(method.getNameAsString()))
                .filter(method -> matchesDeclaringType(astFile, method, declaringTypeName))
                .sorted(Comparator
                        .comparing((MethodDeclaration method) -> declaringTypeRelativeName(method), Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(method -> method.getBegin().map(position -> position.line).orElse(0)))
                .toList();
        if (methodCandidates.isEmpty()) {
            return notFound(normalizedMethodName, declaringTypeName, List.of(), "Method was not found in parsed Java source.");
        }

        var selectedCandidates = argumentCount != null
                ? methodCandidates.stream()
                .filter(method -> method.getParameters().size() == argumentCount)
                .toList()
                : methodCandidates;

        if (selectedCandidates.isEmpty()) {
            return notFound(
                    normalizedMethodName,
                    declaringTypeName,
                    methodCandidates.stream().map(method -> toMatch(astFile, method, GitLabEndpointUseCaseConfidence.LOW)).toList(),
                    "No overload matched argument count " + argumentCount + "."
            );
        }

        var selectedByExpectedTypes = false;
        if (!normalizedExpectedParameterTypes.isEmpty()
                && normalizedExpectedParameterTypes.size() == selectedCandidates.get(0).getParameters().size()) {
            var scoredCandidates = selectedCandidates.stream()
                    .map(method -> new MethodCandidate(method, parameterTypesScore(method, normalizedExpectedParameterTypes)))
                    .filter(candidate -> candidate.score() > 0)
                    .toList();
            var bestScore = scoredCandidates.stream()
                    .mapToInt(MethodCandidate::score)
                    .max()
                    .orElse(0);
            if (bestScore > 0) {
                selectedCandidates = scoredCandidates.stream()
                        .filter(candidate -> candidate.score() == bestScore)
                        .map(MethodCandidate::method)
                        .toList();
                selectedByExpectedTypes = true;
            }
        }

        var eventListenerOverloadsIgnored = false;
        if (selectedCandidates.size() > 1) {
            var nonEventListenerCandidates = selectedCandidates.stream()
                    .filter(method -> !hasEventListenerAnnotation(method))
                    .toList();
            if (nonEventListenerCandidates.size() == 1) {
                selectedCandidates = nonEventListenerCandidates;
                eventListenerOverloadsIgnored = true;
            }
        }

        if (selectedCandidates.size() > 1) {
            return new GitLabJavaMethodResolution(
                    GitLabJavaMethodResolutionStatus.AMBIGUOUS,
                    null,
                    selectedCandidates.stream()
                            .map(method -> toMatch(astFile, method, GitLabEndpointUseCaseConfidence.LOW))
                            .toList(),
                    List.of(ambiguousOverloadLimitation(argumentCount, selectedByExpectedTypes)),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }

        var confidence = eventListenerOverloadsIgnored
                ? GitLabEndpointUseCaseConfidence.MEDIUM
                : GitLabEndpointUseCaseConfidence.HIGH;
        var match = toMatch(astFile, selectedCandidates.get(0), confidence);
        return new GitLabJavaMethodResolution(
                GitLabJavaMethodResolutionStatus.RESOLVED,
                match,
                List.of(match),
                List.of(),
                match.confidence()
        );
    }

    public Optional<MethodDeclaration> methodDeclaration(
            GitLabJavaAstFile astFile,
            GitLabJavaMethodMatch match
    ) {
        if (astFile == null || !astFile.parsed() || match == null) {
            return Optional.empty();
        }
        return astFile.compilationUnit().findAll(MethodDeclaration.class).stream()
                .filter(method -> match.methodName().equals(method.getNameAsString()))
                .filter(method -> match.parameterCount() == method.getParameters().size())
                .filter(method -> match.parameterTypes().equals(method.getParameters().stream()
                        .map(parameter -> parameter.getType().asString())
                        .toList()))
                .filter(method -> match.lineStart() == method.getBegin().map(position -> position.line).orElse(0))
                .filter(method -> match.declaringTypeQualifiedName().equals(qualifiedTypeName(astFile, method)))
                .findFirst();
    }

    private GitLabJavaMethodResolution notFound(
            String methodName,
            String declaringTypeName,
            List<GitLabJavaMethodMatch> candidates,
            String limitation
    ) {
        var target = StringUtils.hasText(declaringTypeName)
                ? declaringTypeName + "#" + methodName
                : methodName;
        return new GitLabJavaMethodResolution(
                GitLabJavaMethodResolutionStatus.NOT_FOUND,
                null,
                candidates,
                List.of(limitation + " Target: " + target + "."),
                GitLabEndpointUseCaseConfidence.LOW
        );
    }

    private boolean matchesDeclaringType(
            GitLabJavaAstFile astFile,
            MethodDeclaration method,
            String declaringTypeName
    ) {
        var normalizedDeclaringTypeName = normalizeTypeName(declaringTypeName);
        if (!StringUtils.hasText(normalizedDeclaringTypeName)) {
            return true;
        }
        var simpleName = simpleName(normalizedDeclaringTypeName);
        var relativeName = declaringTypeRelativeName(method);
        var qualifiedName = qualifiedTypeName(astFile, method);
        return normalizedDeclaringTypeName.equals(simpleName)
                || normalizedDeclaringTypeName.equals(relativeName)
                || normalizedDeclaringTypeName.equals(qualifiedName)
                || (StringUtils.hasText(qualifiedName)
                && qualifiedName.endsWith("." + normalizedDeclaringTypeName));
    }

    private GitLabJavaMethodMatch toMatch(
            GitLabJavaAstFile astFile,
            MethodDeclaration method,
            GitLabEndpointUseCaseConfidence confidence
    ) {
        var type = declaringType(method);
        var modifiers = method.getModifiers().stream()
                .map(modifier -> modifier.getKeyword().asString())
                .toList();
        return new GitLabJavaMethodMatch(
                method.getNameAsString(),
                type != null ? type.getNameAsString() : null,
                type != null ? relativeTypeName(type) : null,
                qualifiedTypeName(astFile, method),
                type != null ? typeKind(type) : GitLabJavaTypeKind.UNKNOWN,
                astFile.path(),
                method.getBegin().map(position -> position.line).orElse(0),
                method.getEnd().map(position -> position.line).orElse(0),
                method.getParameters().size(),
                method.getParameters().stream()
                        .map(parameter -> parameter.getType().asString())
                        .toList(),
                method.getParameters().stream()
                        .map(parameter -> parameter.getNameAsString())
                        .toList(),
                method.getType().asString(),
                modifiers,
                hasModifier(method, Modifier.Keyword.PUBLIC),
                hasModifier(method, Modifier.Keyword.PROTECTED),
                hasModifier(method, Modifier.Keyword.PRIVATE),
                hasModifier(method, Modifier.Keyword.STATIC),
                hasModifier(method, Modifier.Keyword.DEFAULT),
                confidence
        );
    }

    private boolean hasModifier(MethodDeclaration method, Modifier.Keyword keyword) {
        return method.getModifiers().stream()
                .anyMatch(modifier -> modifier.getKeyword() == keyword);
    }

    private String ambiguousOverloadLimitation(Integer argumentCount, boolean selectedByExpectedTypes) {
        if (argumentCount == null) {
            return "More than one method matched; provide argument count to disambiguate overloads.";
        }
        if (selectedByExpectedTypes) {
            return "More than one overload matched method name, argument count and expected parameter types.";
        }
        return "More than one overload matched method name and argument count.";
    }

    private int parameterTypesScore(MethodDeclaration method, List<String> expectedParameterTypes) {
        if (method.getParameters().size() != expectedParameterTypes.size()) {
            return 0;
        }
        var score = 0;
        for (var index = 0; index < expectedParameterTypes.size(); index++) {
            var parameterScore = parameterTypeScore(
                    method.getParameter(index).getType().asString(),
                    expectedParameterTypes.get(index)
            );
            if (parameterScore == 0) {
                return 0;
            }
            score += parameterScore;
        }
        return score;
    }

    private int parameterTypeScore(String actualType, String expectedType) {
        var normalizedActualType = normalizeComparableType(actualType);
        var normalizedExpectedType = normalizeComparableType(expectedType);
        if (!StringUtils.hasText(normalizedActualType) || !StringUtils.hasText(normalizedExpectedType)) {
            return 0;
        }
        if (normalizedActualType.equals(normalizedExpectedType)) {
            return 100;
        }
        if (simpleName(normalizedActualType).equals(simpleName(normalizedExpectedType))) {
            return 80;
        }
        if (normalizedActualType.endsWith("." + normalizedExpectedType)
                || normalizedExpectedType.endsWith("." + normalizedActualType)) {
            return 70;
        }
        return 0;
    }

    private boolean hasEventListenerAnnotation(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .map(annotation -> annotation.getName().asString())
                .map(this::simpleName)
                .anyMatch("EventListener"::equals);
    }

    private String qualifiedTypeName(GitLabJavaAstFile astFile, MethodDeclaration method) {
        var type = declaringType(method);
        if (type == null) {
            return null;
        }
        var relativeName = relativeTypeName(type);
        return StringUtils.hasText(astFile.packageName())
                ? astFile.packageName() + "." + relativeName
                : relativeName;
    }

    private String declaringTypeRelativeName(MethodDeclaration method) {
        var type = declaringType(method);
        return type != null ? relativeTypeName(type) : null;
    }

    private String relativeTypeName(TypeDeclaration<?> declaration) {
        return String.join(".", typeNamePath(declaration));
    }

    private LinkedList<String> typeNamePath(TypeDeclaration<?> declaration) {
        var names = new LinkedList<String>();
        TypeDeclaration<?> current = declaration;
        while (current != null) {
            names.addFirst(current.getNameAsString());
            current = parentType(current);
        }
        return names;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private TypeDeclaration<?> declaringType(MethodDeclaration method) {
        return (TypeDeclaration<?>) method.findAncestor(TypeDeclaration.class).orElse(null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private TypeDeclaration<?> parentType(TypeDeclaration<?> declaration) {
        return (TypeDeclaration<?>) declaration.findAncestor(TypeDeclaration.class).orElse(null);
    }

    private GitLabJavaTypeKind typeKind(TypeDeclaration<?> declaration) {
        if (declaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.isInterface()
                    ? GitLabJavaTypeKind.INTERFACE
                    : GitLabJavaTypeKind.CLASS;
        }
        if (declaration instanceof RecordDeclaration) {
            return GitLabJavaTypeKind.RECORD;
        }
        if (declaration instanceof EnumDeclaration) {
            return GitLabJavaTypeKind.ENUM;
        }
        if (declaration instanceof AnnotationDeclaration) {
            return GitLabJavaTypeKind.ANNOTATION;
        }
        return GitLabJavaTypeKind.UNKNOWN;
    }

    private String simpleName(String typeName) {
        var normalized = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        var dotIndex = normalized.lastIndexOf('.');
        return dotIndex >= 0 ? normalized.substring(dotIndex + 1) : normalized;
    }

    private String normalizeTypeName(String typeName) {
        return GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
    }

    private List<String> normalizeParameterTypes(List<String> parameterTypes) {
        return GitLabEndpointUseCaseModelSupport.copyStrings(parameterTypes).stream()
                .map(this::normalizeComparableType)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String normalizeComparableType(String typeName) {
        var normalized = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        var genericIndex = normalized.indexOf('<');
        if (genericIndex >= 0) {
            normalized = normalized.substring(0, genericIndex).trim();
        }
        while (normalized.endsWith("[]")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        return normalized.replace('$', '.');
    }

    private record MethodCandidate(
            MethodDeclaration method,
            int score
    ) {
    }
}
