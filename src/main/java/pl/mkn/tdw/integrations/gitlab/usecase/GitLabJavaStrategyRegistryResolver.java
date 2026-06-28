package pl.mkn.tdw.integrations.gitlab.usecase;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class GitLabJavaStrategyRegistryResolver {

    private static final Set<String> COLLECTION_TYPES = Set.of(
            "Collection",
            "Iterable",
            "List",
            "Set"
    );
    private static final Set<String> FUNCTIONAL_CONTAINER_TYPES = Set.of(
            "Collection",
            "Iterable",
            "List",
            "Optional",
            "Set",
            "Stream"
    );

    private final GitLabJavaSourceResolver sourceResolver;
    private final GitLabJavaMethodLocator methodLocator;
    private final GitLabJavaInterfaceImplementorResolver implementorResolver;

    public GitLabJavaStrategyRegistryResolver(
            GitLabJavaSourceResolver sourceResolver,
            GitLabJavaMethodLocator methodLocator,
            GitLabJavaInterfaceImplementorResolver implementorResolver
    ) {
        this.sourceResolver = sourceResolver;
        this.methodLocator = methodLocator;
        this.implementorResolver = implementorResolver;
    }

    GitLabJavaStrategyRegistryResolver() {
        this(
                new GitLabJavaSourceResolver(),
                new GitLabJavaMethodLocator(),
                new GitLabJavaInterfaceImplementorResolver()
        );
    }

    GitLabJavaStrategyRegistryResolution resolve(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaAstFile astFile,
            GitLabJavaMethodMatch currentMethod,
            MethodCallExpr methodCall
    ) {
        var scopeName = simpleScopeName(methodCall.getScope().orElse(null));
        if (!StringUtils.hasText(scopeName)) {
            return GitLabJavaStrategyRegistryResolution.notResolved();
        }

        var lambda = lambdaForScope(methodCall, scopeName);
        if (lambda == null) {
            return GitLabJavaStrategyRegistryResolution.notResolved();
        }

        var explicitLambdaType = explicitLambdaParameterType(lambda, scopeName);
        var strategyTypeName = StringUtils.hasText(explicitLambdaType)
                ? explicitLambdaType
                : inferLambdaSourceElementType(astFile, currentMethod, lambda);
        if (!StringUtils.hasText(strategyTypeName)) {
            return GitLabJavaStrategyRegistryResolution.notResolved();
        }

        var pattern = detectRegistryPattern(astFile, currentMethod.declaringTypeQualifiedName(), strategyTypeName);
        if (!pattern.detected()) {
            return GitLabJavaStrategyRegistryResolution.notResolved();
        }

        var strategyType = sourceResolver.resolveType(session, astFile, strategyTypeName);
        if (!strategyType.resolved()
                || strategyType.type() == null
                || strategyType.type().kind() != GitLabJavaTypeKind.INTERFACE) {
            return GitLabJavaStrategyRegistryResolution.notResolved();
        }

        var implementors = implementorResolver.resolveImplementors(session, strategyType);
        var limitations = new ArrayList<String>(implementors.limitations());
        if (implementors.candidates().isEmpty()) {
            return new GitLabJavaStrategyRegistryResolution(
                    strategyType,
                    implementors,
                    List.of(),
                    limitations,
                    pattern
            );
        }

        var candidates = implementors.candidates().stream()
                .map(GitLabJavaStrategyImplementationCandidate::new)
                .toList();

        return new GitLabJavaStrategyRegistryResolution(
                strategyType,
                implementors,
                candidates,
                limitations,
                pattern
        );
    }

    private LambdaExpr lambdaForScope(MethodCallExpr methodCall, String scopeName) {
        var current = methodCall.findAncestor(LambdaExpr.class).orElse(null);
        while (current != null) {
            if (current.getParameters().stream().anyMatch(parameter -> scopeName.equals(parameter.getNameAsString()))) {
                return current;
            }
            current = current.findAncestor(LambdaExpr.class).orElse(null);
        }
        return null;
    }

    private String explicitLambdaParameterType(LambdaExpr lambda, String parameterName) {
        return lambda.getParameters().stream()
                .filter(parameter -> parameterName.equals(parameter.getNameAsString()))
                .filter(parameter -> !parameter.getType().isUnknownType())
                .map(parameter -> parameter.getType().asString())
                .findFirst()
                .orElse(null);
    }

    private String inferLambdaSourceElementType(
            GitLabJavaAstFile astFile,
            GitLabJavaMethodMatch currentMethod,
            LambdaExpr lambda
    ) {
        var consumerCall = lambdaConsumerCall(lambda);
        if (consumerCall == null || consumerCall.getScope().isEmpty()) {
            return null;
        }
        return unwrapFunctionalContainer(inferExpressionType(astFile, currentMethod, consumerCall.getScope().get()));
    }

    private MethodCallExpr lambdaConsumerCall(LambdaExpr lambda) {
        Node current = lambda;
        while (current.getParentNode().isPresent()) {
            var parent = current.getParentNode().get();
            if (parent instanceof MethodCallExpr methodCall) {
                for (var argument : methodCall.getArguments()) {
                    if (argument == current) {
                        return methodCall;
                    }
                }
            }
            current = parent;
        }
        return null;
    }

    private String inferExpressionType(
            GitLabJavaAstFile astFile,
            GitLabJavaMethodMatch currentMethod,
            Expression expression
    ) {
        if (expression == null) {
            return null;
        }
        if (expression.isEnclosedExpr()) {
            return inferExpressionType(astFile, currentMethod, expression.asEnclosedExpr().getInner());
        }
        if (expression.isCastExpr()) {
            return expression.asCastExpr().getType().asString();
        }
        if (expression.isObjectCreationExpr()) {
            return expression.asObjectCreationExpr().getType().asString();
        }
        if (!expression.isMethodCallExpr()) {
            return null;
        }

        var methodCall = expression.asMethodCallExpr();
        if (methodCall.getScope().isEmpty() || methodCall.getScope().get() instanceof ThisExpr) {
            var resolution = methodLocator.resolveMethod(
                    astFile,
                    currentMethod.declaringTypeQualifiedName(),
                    methodCall.getNameAsString(),
                    methodCall.getArguments().size()
            );
            if (resolution.status() == GitLabJavaMethodResolutionStatus.RESOLVED) {
                return resolution.method().returnType();
            }
        }

        if ("stream".equals(methodCall.getNameAsString()) && methodCall.getScope().isPresent()) {
            var scopeName = simpleScopeName(methodCall.getScope().get());
            var fieldType = fieldTypeName(astFile, currentMethod.declaringTypeQualifiedName(), scopeName);
            var elementType = collectionElementType(fieldType);
            if (StringUtils.hasText(elementType)) {
                return elementType;
            }
        }

        if (methodCall.getScope().isPresent()) {
            return inferExpressionType(astFile, currentMethod, methodCall.getScope().get());
        }
        return null;
    }

    private GitLabJavaStrategyRegistryPattern detectRegistryPattern(
            GitLabJavaAstFile astFile,
            String typeName,
            String strategyTypeName
    ) {
        var type = findType(astFile, typeName);
        if (type == null) {
            return GitLabJavaStrategyRegistryPattern.notDetected();
        }

        var collectionFields = new LinkedHashSet<String>();
        var mapFields = new LinkedHashSet<String>();
        for (var field : immediateFields(type)) {
            for (var variable : field.getVariables()) {
                var fieldType = variable.getType().asString();
                if (sameType(collectionElementType(fieldType), strategyTypeName)) {
                    collectionFields.add(variable.getNameAsString());
                }
                if (sameType(mapValueType(fieldType), strategyTypeName)) {
                    mapFields.add(variable.getNameAsString());
                }
            }
        }

        if (collectionFields.isEmpty()) {
            return GitLabJavaStrategyRegistryPattern.notDetected();
        }

        var keyMethods = new LinkedHashSet<String>();
        for (var method : immediateMethods(type)) {
            method.findAll(MethodCallExpr.class).stream()
                    .filter(call -> call.findAncestor(MethodDeclaration.class).orElse(null) == method)
                    .filter(call -> registryKeyMethod(call.getNameAsString()))
                    .filter(call -> callUsesCollectionStrategyParameter(call, collectionFields))
                    .map(MethodCallExpr::getNameAsString)
                    .forEach(keyMethods::add);
        }

        var detected = !mapFields.isEmpty() || !keyMethods.isEmpty();
        return new GitLabJavaStrategyRegistryPattern(
                detected,
                collectionFields.stream().toList(),
                mapFields.stream().toList(),
                keyMethods.stream().toList()
        );
    }

    private boolean callUsesCollectionStrategyParameter(
            MethodCallExpr methodCall,
            Set<String> collectionFieldNames
    ) {
        var scopeName = simpleScopeName(methodCall.getScope().orElse(null));
        if (!StringUtils.hasText(scopeName)) {
            return false;
        }
        var lambda = methodCall.findAncestor(LambdaExpr.class).orElse(null);
        if (lambda == null
                || lambda.getParameters().stream().noneMatch(parameter -> scopeName.equals(parameter.getNameAsString()))) {
            return false;
        }
        var consumerCall = lambdaConsumerCall(lambda);
        if (consumerCall == null || !"forEach".equals(consumerCall.getNameAsString())) {
            return false;
        }
        var collectionScopeName = consumerCall.getScope()
                .map(this::simpleScopeName)
                .orElse(null);
        return collectionFieldNames.contains(collectionScopeName);
    }

    private boolean registryKeyMethod(String methodName) {
        var lower = methodName != null ? methodName.toLowerCase(Locale.ROOT) : "";
        return (lower.startsWith("get") && (lower.contains("type") || lower.contains("key") || lower.contains("code")))
                || lower.startsWith("supported")
                || lower.startsWith("supports")
                || lower.contains("supportedtype")
                || lower.contains("supportstype");
    }

    private List<FieldDeclaration> immediateFields(TypeDeclaration<?> type) {
        return type.findAll(FieldDeclaration.class).stream()
                .filter(field -> field.findAncestor(TypeDeclaration.class).orElse(null) == type)
                .toList();
    }

    private List<MethodDeclaration> immediateMethods(TypeDeclaration<?> type) {
        return type.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.findAncestor(TypeDeclaration.class).orElse(null) == type)
                .toList();
    }

    private TypeDeclaration<?> findType(GitLabJavaAstFile astFile, String typeName) {
        var normalizedTypeName = normalizeTypeName(typeName);
        var simpleName = simpleName(normalizedTypeName);
        return astFile.compilationUnit().findAll(TypeDeclaration.class).stream()
                .map(declaration -> (TypeDeclaration<?>) declaration)
                .filter(declaration -> {
                    var relativeName = relativeTypeName(declaration);
                    var qualifiedName = qualifiedTypeName(astFile, declaration);
                    return !StringUtils.hasText(normalizedTypeName)
                            || normalizedTypeName.equals(declaration.getNameAsString())
                            || normalizedTypeName.equals(relativeName)
                            || normalizedTypeName.equals(qualifiedName)
                            || simpleName.equals(declaration.getNameAsString())
                            || (StringUtils.hasText(qualifiedName)
                            && qualifiedName.endsWith("." + normalizedTypeName));
                })
                .findFirst()
                .orElse(null);
    }

    private String fieldTypeName(GitLabJavaAstFile astFile, String ownerTypeName, String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return null;
        }
        var ownerType = findType(astFile, ownerTypeName);
        if (ownerType == null) {
            return null;
        }
        return immediateFields(ownerType).stream()
                .flatMap(field -> field.getVariables().stream())
                .filter(variable -> fieldName.equals(variable.getNameAsString()))
                .map(variable -> variable.getType().asString())
                .findFirst()
                .orElse(null);
    }

    private String collectionElementType(String typeName) {
        var raw = simpleName(typeName);
        var args = genericArguments(typeName);
        return COLLECTION_TYPES.contains(raw) && !args.isEmpty() ? args.get(0) : null;
    }

    private String mapValueType(String typeName) {
        var raw = simpleName(typeName);
        var args = genericArguments(typeName);
        return "Map".equals(raw) && args.size() >= 2 ? args.get(1) : null;
    }

    private String unwrapFunctionalContainer(String typeName) {
        var raw = simpleName(typeName);
        var args = genericArguments(typeName);
        if (FUNCTIONAL_CONTAINER_TYPES.contains(raw) && !args.isEmpty()) {
            return args.get(0);
        }
        return typeName;
    }

    private List<String> genericArguments(String typeName) {
        var value = GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
        if (value == null) {
            return List.of();
        }
        var start = value.indexOf('<');
        var end = value.lastIndexOf('>');
        if (start < 0 || end <= start) {
            return List.of();
        }
        var inside = value.substring(start + 1, end);
        var result = new ArrayList<String>();
        var depth = 0;
        var current = new StringBuilder();
        for (var index = 0; index < inside.length(); index++) {
            var character = inside.charAt(index);
            if (character == '<') {
                depth++;
            } else if (character == '>') {
                depth--;
            }
            if (character == ',' && depth == 0) {
                addGenericArgument(result, current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        addGenericArgument(result, current.toString());
        return result;
    }

    private void addGenericArgument(List<String> result, String value) {
        var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(value);
        if (normalized == null) {
            return;
        }
        while (normalized.startsWith("? extends ")) {
            normalized = normalized.substring("? extends ".length()).trim();
        }
        while (normalized.startsWith("? super ")) {
            normalized = normalized.substring("? super ".length()).trim();
        }
        result.add(normalized);
    }

    private boolean sameType(String left, String right) {
        var normalizedLeft = normalizeTypeName(left);
        var normalizedRight = normalizeTypeName(right);
        if (!StringUtils.hasText(normalizedLeft) || !StringUtils.hasText(normalizedRight)) {
            return false;
        }
        return normalizedLeft.equals(normalizedRight)
                || simpleName(normalizedLeft).equals(simpleName(normalizedRight));
    }

    private String simpleScopeName(Expression scope) {
        if (scope instanceof NameExpr nameExpr) {
            return nameExpr.getNameAsString();
        }
        if (scope instanceof FieldAccessExpr fieldAccessExpr && fieldAccessExpr.getScope() instanceof ThisExpr) {
            return fieldAccessExpr.getNameAsString();
        }
        return null;
    }

    private String qualifiedTypeName(GitLabJavaAstFile astFile, TypeDeclaration<?> declaration) {
        var relativeName = relativeTypeName(declaration);
        return StringUtils.hasText(astFile.packageName())
                ? astFile.packageName() + "." + relativeName
                : relativeName;
    }

    private String relativeTypeName(TypeDeclaration<?> declaration) {
        var names = new java.util.LinkedList<String>();
        TypeDeclaration<?> current = declaration;
        while (current != null) {
            names.addFirst(current.getNameAsString());
            @SuppressWarnings({"rawtypes", "unchecked"})
            var parent = (TypeDeclaration<?>) current.findAncestor(TypeDeclaration.class).orElse(null);
            current = parent;
        }
        return String.join(".", names);
    }

    private String simpleName(String typeName) {
        var normalized = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        var dotIndex = normalized.lastIndexOf('.');
        return dotIndex >= 0 ? normalized.substring(dotIndex + 1) : normalized;
    }

    private String normalizeTypeName(String typeName) {
        var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
        if (normalized == null) {
            return "";
        }
        while (normalized.startsWith("? extends ")) {
            normalized = normalized.substring("? extends ".length()).trim();
        }
        while (normalized.startsWith("? super ")) {
            normalized = normalized.substring("? super ".length()).trim();
        }
        while (normalized.endsWith("[]")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        var genericIndex = normalized.indexOf('<');
        if (genericIndex >= 0) {
            normalized = normalized.substring(0, genericIndex).trim();
        }
        return normalized.replace('$', '.');
    }
}

record GitLabJavaStrategyRegistryResolution(
        GitLabJavaResolvedType strategyType,
        GitLabJavaImplementorResolution implementors,
        List<GitLabJavaStrategyImplementationCandidate> candidates,
        List<String> limitations,
        GitLabJavaStrategyRegistryPattern pattern
) {

    static GitLabJavaStrategyRegistryResolution notResolved() {
        return new GitLabJavaStrategyRegistryResolution(null, null, List.of(), List.of(),
                GitLabJavaStrategyRegistryPattern.notDetected());
    }

    GitLabJavaStrategyRegistryResolution {
        candidates = GitLabEndpointUseCaseModelSupport.copy(candidates);
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
        pattern = pattern != null ? pattern : GitLabJavaStrategyRegistryPattern.notDetected();
    }

    boolean resolved() {
        return strategyType != null;
    }
}

record GitLabJavaStrategyImplementationCandidate(
        GitLabJavaImplementorCandidate implementor
) {
}

record GitLabJavaStrategyRegistryPattern(
        boolean detected,
        List<String> collectionFields,
        List<String> mapFields,
        List<String> keyMethods
) {

    static GitLabJavaStrategyRegistryPattern notDetected() {
        return new GitLabJavaStrategyRegistryPattern(false, List.of(), List.of(), List.of());
    }

    GitLabJavaStrategyRegistryPattern {
        collectionFields = GitLabEndpointUseCaseModelSupport.copyStrings(collectionFields);
        mapFields = GitLabEndpointUseCaseModelSupport.copyStrings(mapFields);
        keyMethods = GitLabEndpointUseCaseModelSupport.copyStrings(keyMethods);
    }
}
