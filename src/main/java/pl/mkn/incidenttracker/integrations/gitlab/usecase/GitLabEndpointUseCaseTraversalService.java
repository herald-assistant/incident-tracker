package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GitLabEndpointUseCaseTraversalService {

    private static final Pattern YAML_PATH_PATTERN = Pattern.compile("([\\w./-]+\\.ya?ml)");
    private static final Pattern TYPE_TOKEN_PATTERN = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b");
    private static final Set<String> TERMINAL_STATIC_SCOPES = Set.of(
            "Assert",
            "CollectionUtils",
            "Collections",
            "List",
            "Map",
            "Math",
            "Objects",
            "Optional",
            "ResponseEntity",
            "Set",
            "StringUtils",
            "UUID"
    );

    private final GitLabJavaSourceResolver sourceResolver;
    private final GitLabJavaMethodLocator methodLocator;
    private final GitLabJavaDependencyModelBuilder dependencyModelBuilder;
    private final GitLabJavaInterfaceImplementorResolver implementorResolver;
    private final GitLabJavaSpringDataRepositoryDetector springDataRepositoryDetector;
    private final GitLabJavaMapStructResolver mapStructResolver;
    private final GitLabEndpointUseCaseResultCompressor resultCompressor;

    public GitLabEndpointUseCaseTraversalService(
            GitLabJavaSourceResolver sourceResolver,
            GitLabJavaMethodLocator methodLocator,
            GitLabJavaDependencyModelBuilder dependencyModelBuilder,
            GitLabJavaInterfaceImplementorResolver implementorResolver,
            GitLabJavaSpringDataRepositoryDetector springDataRepositoryDetector,
            GitLabJavaMapStructResolver mapStructResolver,
            GitLabEndpointUseCaseResultCompressor resultCompressor
    ) {
        this.sourceResolver = sourceResolver;
        this.methodLocator = methodLocator;
        this.dependencyModelBuilder = dependencyModelBuilder;
        this.implementorResolver = implementorResolver;
        this.springDataRepositoryDetector = springDataRepositoryDetector;
        this.mapStructResolver = mapStructResolver;
        this.resultCompressor = resultCompressor;
    }

    GitLabEndpointUseCaseTraversalService() {
        this(
                new GitLabJavaSourceResolver(),
                new GitLabJavaMethodLocator(),
                new GitLabJavaDependencyModelBuilder(),
                new GitLabJavaInterfaceImplementorResolver(),
                new GitLabJavaSpringDataRepositoryDetector(),
                new GitLabJavaMapStructResolver(),
                new GitLabEndpointUseCaseResultCompressor()
        );
    }

    public GitLabEndpointUseCaseContextResult traverse(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseEndpointContext endpoint,
            GitLabEndpointUseCaseLimits limits
    ) {
        Objects.requireNonNull(session, "session must not be null");
        var effectiveLimits = limits != null ? limits : GitLabEndpointUseCaseLimits.defaults();
        var repository = session.repository();
        var state = new GitLabEndpointUseCaseTraversalState(
                repository,
                endpoint,
                effectiveLimits.maxDepth(),
                effectiveLimits.maxFiles()
        );
        if (endpoint == null || !StringUtils.hasText(endpoint.filePath())) {
            state.addLimitation("Resolved endpoint with source file is required before traversal.");
            return resultCompressor.compress(state.toResult(session));
        }

        state.addLimitations(endpoint.limitations());
        var openApiContractPaths = addOpenApiContractFiles(state, endpoint);
        addOpenApiGeneratedModelSymbols(state, endpoint, openApiContractPaths);
        var handlerSymbol = symbol(endpoint.controllerClass(), endpoint.handlerMethod());
        state.addFile(
                endpoint.filePath(),
                GitLabEndpointUseCaseFileRole.CONTROLLER,
                endpoint.handlerMethod(),
                "Endpoint handler and local controller flow.",
                endpoint.confidence()
        );
        state.addRelation(
                endpoint.endpointId() != null ? endpoint.endpointId() : endpoint.path(),
                handlerSymbol,
                GitLabEndpointUseCaseRelationKind.ENDPOINT_HANDLER,
                endpoint.confidence(),
                "Endpoint inventory resolved this handler method."
        );
        state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                endpoint.filePath(),
                endpoint.controllerClass(),
                endpoint.handlerMethod(),
                null,
                0,
                GitLabEndpointUseCaseFileRole.CONTROLLER,
                endpoint.confidence(),
                "Traversal starts from endpoint handler."
        ));

        GitLabEndpointUseCaseTraversalNode node;
        while ((node = state.poll()) != null) {
            visitNode(session, state, node);
        }
        return resultCompressor.compress(state.toResult(session));
    }

    private void visitNode(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabEndpointUseCaseTraversalNode node
    ) {
        if (!state.markVisited(node)) {
            return;
        }

        var astFile = sourceResolver.astFile(session, node.filePath());
        if (!astFile.parsed()) {
            state.addLimitations(astFile.limitations());
            state.addUnresolved(
                    symbol(node.typeName(), node.methodName()),
                    node.filePath(),
                    "Source file could not be parsed while traversing method.",
                    List.of(node.methodName()),
                    List.of(node.filePath())
            );
            return;
        }

        var methodResolution = methodLocator.resolveMethod(
                astFile,
                node.typeName(),
                node.methodName(),
                node.argumentCount(),
                node.parameterTypes()
        );
        if (methodResolution.status() == GitLabJavaMethodResolutionStatus.RESOLVED) {
            visitResolvedMethod(session, state, node, astFile, methodResolution.method(), methodResolution.confidence());
            return;
        }

        state.addLimitations(methodResolution.limitations());
        if (methodResolution.status() == GitLabJavaMethodResolutionStatus.AMBIGUOUS
                && !methodResolution.candidates().isEmpty()) {
            state.addLimitation("Ambiguous method resolution is traversed best-effort for "
                    + symbol(node.typeName(), node.methodName()) + ".");
            methodResolution.candidates().stream()
                    .limit(4)
                    .forEach(candidate -> visitResolvedMethod(
                            session,
                            state,
                            node,
                            astFile,
                            candidate,
                            GitLabEndpointUseCaseConfidence.MEDIUM
                    ));
            return;
        }

        state.addUnresolved(
                symbol(node.typeName(), node.methodName()),
                node.filePath(),
                "Method could not be resolved for traversal: " + methodResolution.status() + ".",
                List.of(node.methodName()),
                methodResolution.candidates().stream()
                        .map(candidate -> symbol(candidate.declaringTypeQualifiedName(), candidate.methodName()))
                        .toList()
        );
    }

    private void visitResolvedMethod(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabEndpointUseCaseTraversalNode node,
            GitLabJavaAstFile astFile,
            GitLabJavaMethodMatch match,
            GitLabEndpointUseCaseConfidence confidence
    ) {
        state.addMethod(match, node.role(), node.depth(), node.reason(), confidence);
        var method = methodLocator.methodDeclaration(astFile, match).orElse(null);
        if (method == null) {
            state.addUnresolved(
                    symbol(match.declaringTypeQualifiedName(), match.methodName()),
                    match.filePath(),
                    "Resolved method match could not be mapped back to JavaParser node.",
                    List.of(match.methodName()),
                    List.of(match.filePath())
            );
            return;
        }

        addMethodSignatureTypes(session, state, astFile, match);
        var dependencies = dependencyModelBuilder.build(astFile, match.declaringTypeQualifiedName());
        state.addLimitations(dependencies.limitations());
        var dependenciesByName = dependenciesByName(dependencies);
        var localTypes = localTypes(session, astFile, match, dependenciesByName, method);
        var currentSymbol = symbol(match.declaringTypeQualifiedName(), match.methodName());

        for (var methodCall : directMethodCalls(method)) {
            if (processMapperCall(session, state, astFile, node, currentSymbol, methodCall)) {
                continue;
            }
            if (processLocalHelperCall(state, astFile, node, match, currentSymbol, methodCall)) {
                continue;
            }
            if (processInjectedDependencyCall(session, state, astFile, node, currentSymbol, dependenciesByName, methodCall)) {
                continue;
            }
            if (processVariableDomainCall(session, state, astFile, node, currentSymbol, localTypes, methodCall)) {
                continue;
            }
            if (processStaticCall(session, state, astFile, node, currentSymbol, methodCall)) {
                continue;
            }
            addUnresolvedFieldScopedCall(session, state, astFile, match, dependenciesByName, localTypes, methodCall);
        }

        for (var creation : directObjectCreations(method)) {
            addResolvedType(
                    session,
                    state,
                    astFile,
                    creation.getType().asString(),
                    roleForTypeName(creation.getType().asString(), null),
                    creation.getType().getNameAsString(),
                    "Type is instantiated inside traversed method.",
                    GitLabEndpointUseCaseConfidence.MEDIUM
            );
        }
    }

    private boolean processMapperCall(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabJavaAstFile astFile,
            GitLabEndpointUseCaseTraversalNode node,
            String currentSymbol,
            MethodCallExpr methodCall
    ) {
        var scope = methodCall.getScope().orElse(null);
        if (scope == null) {
            return false;
        }
        var scopeText = scope.toString();
        if (scopeText.endsWith(".INSTANCE")) {
            var mapperType = scopeText.substring(0, scopeText.length() - ".INSTANCE".length());
            var resolved = addResolvedType(
                    session,
                    state,
                    astFile,
                    mapperType,
                    GitLabEndpointUseCaseFileRole.MAPPER,
                    methodCall.getNameAsString(),
                    "MapStruct mapper called from traversed method.",
                    GitLabEndpointUseCaseConfidence.HIGH
            );
            state.addRelation(
                    currentSymbol,
                    symbol(mapperType, methodCall.getNameAsString()),
                    GitLabEndpointUseCaseRelationKind.MAPPER_CALL,
                    GitLabEndpointUseCaseConfidence.HIGH,
                    "Mapper.INSTANCE call in source."
            );
            if (resolved != null && resolved.resolved()) {
                addMapStructUses(session, state, resolved, currentSymbol);
                state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                        resolved.filePath(),
                        resolved.qualifiedName() != null ? resolved.qualifiedName() : mapperType,
                        methodCall.getNameAsString(),
                        methodCall.getArguments().size(),
                        node.depth() + 1,
                        GitLabEndpointUseCaseFileRole.MAPPER,
                        resolved.confidence(),
                        "Mapper method called from traversed flow."
                ));
            }
            return true;
        }
        if ("getMapper".equals(methodCall.getNameAsString()) && isMapStructFactory(scopeText)
                && !methodCall.getArguments().isEmpty()
                && methodCall.getArgument(0) instanceof ClassExpr classExpr) {
            var mapperType = classExpr.getType().asString();
            var resolved = addResolvedType(
                    session,
                    state,
                    astFile,
                    mapperType,
                    GitLabEndpointUseCaseFileRole.MAPPER,
                    "INSTANCE",
                    "MapStruct factory obtains mapper instance.",
                    GitLabEndpointUseCaseConfidence.MEDIUM
            );
            if (resolved != null && resolved.resolved()) {
                addMapStructUses(session, state, resolved, currentSymbol);
            }
            return true;
        }
        return false;
    }

    private boolean processLocalHelperCall(
            GitLabEndpointUseCaseTraversalState state,
            GitLabJavaAstFile astFile,
            GitLabEndpointUseCaseTraversalNode node,
            GitLabJavaMethodMatch currentMethod,
            String currentSymbol,
            MethodCallExpr methodCall
    ) {
        var scope = methodCall.getScope().orElse(null);
        if (scope != null && !(scope instanceof ThisExpr)) {
            return false;
        }
        var resolution = methodLocator.resolveMethod(
                astFile,
                currentMethod.declaringTypeQualifiedName(),
                methodCall.getNameAsString(),
                methodCall.getArguments().size()
        );
        if (resolution.status() != GitLabJavaMethodResolutionStatus.RESOLVED) {
            return false;
        }
        var helper = resolution.method();
        var helperSymbol = symbol(helper.declaringTypeQualifiedName(), helper.methodName());
        if (helperSymbol.equals(currentSymbol)) {
            return false;
        }
        state.addFile(
                helper.filePath(),
                node.role(),
                helper.methodName(),
                "Local helper method used by endpoint flow.",
                resolution.confidence()
        );
        state.addRelation(
                currentSymbol,
                helperSymbol,
                GitLabEndpointUseCaseRelationKind.LOCAL_METHOD_CALL,
                resolution.confidence(),
                "Unscoped or this-scoped method call in the same type."
        );
        state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                helper.filePath(),
                helper.declaringTypeQualifiedName(),
                helper.methodName(),
                helper.parameterCount(),
                node.depth() + 1,
                node.role(),
                resolution.confidence(),
                "Local helper called from traversed method."
        ));
        return true;
    }

    private boolean processInjectedDependencyCall(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabJavaAstFile astFile,
            GitLabEndpointUseCaseTraversalNode node,
            String currentSymbol,
            Map<String, GitLabJavaInjectedDependency> dependenciesByName,
            MethodCallExpr methodCall
    ) {
        var scopeName = simpleScopeName(methodCall.getScope().orElse(null));
        if (!StringUtils.hasText(scopeName)) {
            return false;
        }
        var dependency = dependenciesByName.get(scopeName);
        if (dependency == null) {
            return false;
        }

        var dependencyType = sourceResolver.resolveType(session, astFile, dependency.typeName());
        if (!dependencyType.resolved()) {
            addUnresolvedType(state, dependency.typeName(), dependency.filePath(), dependencyType);
            return true;
        }

        var portRole = roleForTypeName(dependency.typeName(), dependencyType.filePath());
        state.addFile(
                dependencyType.filePath(),
                portRole,
                methodCall.getNameAsString(),
                "Injected dependency used by traversed method: " + dependency.name() + ".",
                dependencyType.confidence()
        );
        state.addRelation(
                currentSymbol,
                symbol(dependencyType.qualifiedName() != null ? dependencyType.qualifiedName() : dependency.typeName(),
                        methodCall.getNameAsString()),
                GitLabEndpointUseCaseRelationKind.INJECTED_PORT_CALL,
                dependency.confidence(),
                "Method call on injected dependency " + dependency.name() + "."
        );

        if (addSpringDataBoundary(session, state, dependencyType, methodCall.getNameAsString(), currentSymbol)) {
            return true;
        }

        if (dependencyType.type() != null && dependencyType.type().kind() == GitLabJavaTypeKind.INTERFACE) {
            addInterfaceImplementations(session, state, node, currentSymbol, dependencyType, methodCall);
        } else {
            state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                    dependencyType.filePath(),
                    dependencyType.qualifiedName() != null ? dependencyType.qualifiedName() : dependency.typeName(),
                    methodCall.getNameAsString(),
                    methodCall.getArguments().size(),
                    node.depth() + 1,
                    roleForTypeName(dependencyType.qualifiedName() != null ? dependencyType.qualifiedName() : dependency.typeName(),
                            dependencyType.filePath()),
                    dependencyType.confidence(),
                    "Method called on injected concrete dependency."
            ));
        }
        return true;
    }

    private boolean processVariableDomainCall(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabJavaAstFile astFile,
            GitLabEndpointUseCaseTraversalNode node,
            String currentSymbol,
            Map<String, String> localTypes,
            MethodCallExpr methodCall
    ) {
        var scopeName = simpleScopeName(methodCall.getScope().orElse(null));
        if (!StringUtils.hasText(scopeName) || isAccessorLike(methodCall.getNameAsString())) {
            return false;
        }
        var typeName = localTypes.get(scopeName);
        if (!StringUtils.hasText(typeName)) {
            return false;
        }
        var role = roleForTypeName(typeName, null);
        if (role == GitLabEndpointUseCaseFileRole.WEB_MODEL || role == GitLabEndpointUseCaseFileRole.PROJECTION) {
            return false;
        }
        var resolved = addResolvedType(
                session,
                state,
                astFile,
                typeName,
                role,
                methodCall.getNameAsString(),
                "Domain object method called inside traversed method.",
                GitLabEndpointUseCaseConfidence.MEDIUM
        );
        if (resolved == null || !resolved.resolved()) {
            return true;
        }
        state.addRelation(
                currentSymbol,
                symbol(resolved.qualifiedName() != null ? resolved.qualifiedName() : typeName, methodCall.getNameAsString()),
                GitLabEndpointUseCaseRelationKind.DOMAIN_METHOD_CALL,
                GitLabEndpointUseCaseConfidence.MEDIUM,
                "Method call on local variable or parameter " + scopeName + "."
        );
        state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                resolved.filePath(),
                resolved.qualifiedName() != null ? resolved.qualifiedName() : typeName,
                methodCall.getNameAsString(),
                methodCall.getArguments().size(),
                node.depth() + 1,
                role,
                resolved.confidence(),
                "Domain method called from traversed flow."
        ));
        if (resolved.type() != null && resolved.type().kind() == GitLabJavaTypeKind.INTERFACE) {
            addDomainInterfaceImplementations(session, state, node, currentSymbol, resolved, methodCall);
        }
        return true;
    }

    private boolean processStaticCall(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabJavaAstFile astFile,
            GitLabEndpointUseCaseTraversalNode node,
            String currentSymbol,
            MethodCallExpr methodCall
    ) {
        var scope = methodCall.getScope().orElse(null);
        if (scope == null) {
            return false;
        }
        var scopeText = scope.toString();
        if (isConstantLike(scopeText)
                || !looksLikeTypeName(scopeText)
                || TERMINAL_STATIC_SCOPES.contains(simpleName(scopeText))) {
            return false;
        }
        var resolved = addResolvedType(
                session,
                state,
                astFile,
                scopeText,
                roleForTypeName(scopeText, null),
                methodCall.getNameAsString(),
                "Static method call from traversed method.",
                GitLabEndpointUseCaseConfidence.MEDIUM
        );
        if (resolved == null || !resolved.resolved()) {
            return true;
        }
        state.addRelation(
                currentSymbol,
                symbol(resolved.qualifiedName() != null ? resolved.qualifiedName() : scopeText, methodCall.getNameAsString()),
                GitLabEndpointUseCaseRelationKind.STATIC_METHOD_CALL,
                GitLabEndpointUseCaseConfidence.MEDIUM,
                "Static call resolved by source lookup."
        );
        if (isLombokGeneratedStaticCall(session, resolved, methodCall.getNameAsString())) {
            return true;
        }
        state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                resolved.filePath(),
                resolved.qualifiedName() != null ? resolved.qualifiedName() : scopeText,
                methodCall.getNameAsString(),
                methodCall.getArguments().size(),
                node.depth() + 1,
                roleForTypeName(scopeText, resolved.filePath()),
                resolved.confidence(),
                "Static helper called from traversed flow."
        ));
        return true;
    }

    private void addUnresolvedFieldScopedCall(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabJavaAstFile astFile,
            GitLabJavaMethodMatch match,
            Map<String, GitLabJavaInjectedDependency> dependenciesByName,
            Map<String, String> localTypes,
            MethodCallExpr methodCall
    ) {
        var scope = methodCall.getScope().orElse(null);
        var scopeName = simpleScopeName(scope);
        if (!StringUtils.hasText(scopeName)
                || dependenciesByName.containsKey(scopeName)
                || localTypes.containsKey(scopeName)
                || isAccessorLike(methodCall.getNameAsString())) {
            return;
        }
        var scopeText = scope != null ? scope.toString() : null;
        if (looksLikeTypeName(scopeText) || isConstantLike(scopeText)) {
            return;
        }

        var fieldTypeName = fieldTypeName(astFile, match.declaringTypeQualifiedName(), scopeName);
        if (!StringUtils.hasText(fieldTypeName)) {
            return;
        }

        var resolved = sourceResolver.resolveType(session, astFile, fieldTypeName);
        var candidates = resolved.resolved() ? List.of(resolved.filePath()) : List.<String>of();
        state.addUnresolved(
                symbol(fieldTypeName, methodCall.getNameAsString()),
                astFile.path(),
                "Scoped method call uses a class field that is not recognized as an injected dependency or local type; traversal may omit this branch.",
                List.of(scopeName, fieldTypeName, methodCall.getNameAsString()),
                candidates
        );
    }

    private boolean isLombokGeneratedStaticCall(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaResolvedType resolved,
            String methodName
    ) {
        if (!"builder".equals(methodName) || resolved == null || !resolved.resolved()) {
            return false;
        }
        var astFile = sourceResolver.astFile(session, resolved.filePath());
        if (!astFile.parsed()) {
            return false;
        }
        var type = findType(astFile, resolved.qualifiedName() != null ? resolved.qualifiedName() : resolved.requestedName());
        if (type == null) {
            return false;
        }
        return hasAnnotation(type, "Builder")
                || immediateConstructors(type).stream().anyMatch(constructor -> hasAnnotation(constructor, "Builder"))
                || immediateMethods(type).stream().anyMatch(method -> hasAnnotation(method, "Builder"));
    }

    private void addInterfaceImplementations(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabEndpointUseCaseTraversalNode node,
            String currentSymbol,
            GitLabJavaResolvedType dependencyType,
            MethodCallExpr methodCall
    ) {
        var implementors = implementorResolver.resolveImplementors(session, dependencyType);
        state.addLimitations(implementors.limitations());
        if (implementors.status() == GitLabJavaImplementorResolutionStatus.NOT_FOUND
                || implementors.status() == GitLabJavaImplementorResolutionStatus.INVALID_REQUEST) {
            state.addUnresolved(
                    dependencyType.requestedName(),
                    dependencyType.filePath(),
                    "No implementation was resolved for injected interface dependency.",
                    implementors.searchKeywords(),
                    List.of()
            );
            return;
        }
        if (implementors.status() == GitLabJavaImplementorResolutionStatus.AMBIGUOUS) {
            state.addUnresolved(
                    dependencyType.requestedName(),
                    dependencyType.filePath(),
                    "More than one implementation matched injected interface dependency.",
                    implementors.searchKeywords(),
                    implementors.candidates().stream().map(GitLabJavaImplementorCandidate::filePath).distinct().toList()
            );
        }
        var interfaceParameterTypes = interfaceMethodParameterTypes(session, dependencyType, methodCall);
        for (var candidate : implementors.candidates()) {
            var role = roleForTypeName(candidate.implementationQualifiedName(), candidate.filePath());
            state.addFile(
                    candidate.filePath(),
                    role,
                    methodCall.getNameAsString(),
                    candidate.reason(),
                    candidate.confidence()
            );
            state.addRelation(
                    symbol(dependencyType.qualifiedName() != null ? dependencyType.qualifiedName() : dependencyType.requestedName(),
                            methodCall.getNameAsString()),
                    symbol(candidate.implementationQualifiedName(), methodCall.getNameAsString()),
                    GitLabEndpointUseCaseRelationKind.INTERFACE_IMPLEMENTATION,
                    candidate.confidence(),
                    "Implementation candidate for injected interface."
            );
            var implementationAstFile = sourceResolver.astFile(session, candidate.filePath());
            var implementationType = new GitLabJavaResolvedType(
                    candidate.implementationQualifiedName(),
                    GitLabJavaTypeResolutionKind.TREE_LOOKUP,
                    candidate.confidence(),
                    null,
                    candidate.filePath(),
                    candidate.implementationQualifiedName(),
                    List.of(candidate.filePath()),
                    List.of()
            );
            if (addSpringDataBoundary(session, state, implementationType, methodCall.getNameAsString(), currentSymbol)) {
                continue;
            }
            if (implementationAstFile.parsed()
                    && springDataRepositoryDetector.detect(implementationAstFile, candidate.implementationQualifiedName()).status()
                    == GitLabJavaSpringDataRepositoryStatus.DETECTED) {
                continue;
            }
            state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                    candidate.filePath(),
                    candidate.implementationQualifiedName(),
                    methodCall.getNameAsString(),
                    methodCall.getArguments().size(),
                    interfaceParameterTypes,
                    node.depth() + 1,
                    role,
                    candidate.confidence(),
                    "Implementation method for injected interface call."
            ));
        }
    }

    private void addDomainInterfaceImplementations(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabEndpointUseCaseTraversalNode node,
            String currentSymbol,
            GitLabJavaResolvedType interfaceType,
            MethodCallExpr methodCall
    ) {
        var implementors = implementorResolver.resolveImplementors(session, interfaceType);
        state.addLimitations(implementors.limitations());
        if (implementors.status() == GitLabJavaImplementorResolutionStatus.NOT_FOUND
                || implementors.status() == GitLabJavaImplementorResolutionStatus.INVALID_REQUEST) {
            state.addUnresolved(
                    interfaceType.requestedName(),
                    interfaceType.filePath(),
                    "No implementation was resolved for domain interface variable.",
                    implementors.searchKeywords(),
                    List.of()
            );
            return;
        }
        if (implementors.status() == GitLabJavaImplementorResolutionStatus.AMBIGUOUS) {
            state.addLimitation("More than one implementation matched domain interface variable "
                    + interfaceType.requestedName() + "; all candidates are traversed best-effort.");
        }
        var interfaceParameterTypes = interfaceMethodParameterTypes(session, interfaceType, methodCall);
        for (var candidate : implementors.candidates()) {
            var role = roleForTypeName(candidate.implementationQualifiedName(), candidate.filePath());
            state.addFile(
                    candidate.filePath(),
                    role,
                    methodCall.getNameAsString(),
                    candidate.reason(),
                    candidate.confidence()
            );
            state.addRelation(
                    symbol(interfaceType.qualifiedName() != null ? interfaceType.qualifiedName() : interfaceType.requestedName(),
                            methodCall.getNameAsString()),
                    symbol(candidate.implementationQualifiedName(), methodCall.getNameAsString()),
                    GitLabEndpointUseCaseRelationKind.INTERFACE_IMPLEMENTATION,
                    candidate.confidence(),
                    "Implementation candidate for domain interface method call."
            );
            state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                    candidate.filePath(),
                    candidate.implementationQualifiedName(),
                    methodCall.getNameAsString(),
                    methodCall.getArguments().size(),
                    interfaceParameterTypes,
                    node.depth() + 1,
                    role,
                    candidate.confidence(),
                    "Domain interface implementation method called from traversed flow."
            ));
            addDomainSubtypeOverrides(session, state, node, methodCall, interfaceParameterTypes, candidate);
        }
    }

    private void addDomainSubtypeOverrides(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabEndpointUseCaseTraversalNode node,
            MethodCallExpr methodCall,
            List<String> interfaceParameterTypes,
            GitLabJavaImplementorCandidate baseCandidate
    ) {
        var subtypes = implementorResolver.resolveSubtypes(session, baseCandidate);
        state.addLimitations(subtypes.limitations());
        if (subtypes.candidates().isEmpty()) {
            return;
        }

        var baseSymbol = symbol(baseCandidate.implementationQualifiedName(), methodCall.getNameAsString());
        for (var subtype : subtypes.candidates()) {
            var subtypeAstFile = sourceResolver.astFile(session, subtype.filePath());
            if (!subtypeAstFile.parsed()) {
                continue;
            }
            var resolution = methodLocator.resolveMethod(
                    subtypeAstFile,
                    subtype.implementationQualifiedName(),
                    methodCall.getNameAsString(),
                    methodCall.getArguments().size(),
                    interfaceParameterTypes
            );
            if (resolution.status() == GitLabJavaMethodResolutionStatus.NOT_FOUND
                    || resolution.status() == GitLabJavaMethodResolutionStatus.INVALID_REQUEST
                    || resolution.status() == GitLabJavaMethodResolutionStatus.PARSE_FAILED) {
                continue;
            }
            var role = roleForTypeName(subtype.implementationQualifiedName(), subtype.filePath());
            state.addFile(
                    subtype.filePath(),
                    role,
                    methodCall.getNameAsString(),
                    "Subtype overrides domain interface method through " + baseCandidate.implementationSimpleName() + ".",
                    subtype.confidence()
            );
            state.addRelation(
                    baseSymbol,
                    symbol(subtype.implementationQualifiedName(), methodCall.getNameAsString()),
                    GitLabEndpointUseCaseRelationKind.INTERFACE_IMPLEMENTATION,
                    subtype.confidence(),
                    "Subtype override candidate for domain interface method call."
            );
            state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                    subtype.filePath(),
                    subtype.implementationQualifiedName(),
                    methodCall.getNameAsString(),
                    methodCall.getArguments().size(),
                    interfaceParameterTypes,
                    node.depth() + 1,
                    role,
                    subtype.confidence(),
                    "Domain subtype override method called from traversed flow."
            ));
        }
    }

    private List<String> interfaceMethodParameterTypes(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaResolvedType interfaceType,
            MethodCallExpr methodCall
    ) {
        if (interfaceType == null || !interfaceType.resolved() || !StringUtils.hasText(interfaceType.filePath())) {
            return List.of();
        }
        var interfaceAstFile = sourceResolver.astFile(session, interfaceType.filePath());
        if (!interfaceAstFile.parsed()) {
            return List.of();
        }
        var resolution = methodLocator.resolveMethod(
                interfaceAstFile,
                interfaceType.qualifiedName() != null ? interfaceType.qualifiedName() : interfaceType.requestedName(),
                methodCall.getNameAsString(),
                methodCall.getArguments().size()
        );
        return resolution.status() == GitLabJavaMethodResolutionStatus.RESOLVED
                ? resolution.method().parameterTypes()
                : List.of();
    }

    private GitLabJavaResolvedType addResolvedType(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabJavaAstFile context,
            String typeName,
            GitLabEndpointUseCaseFileRole preferredRole,
            String symbol,
            String reason,
            GitLabEndpointUseCaseConfidence confidence
    ) {
        if (isTerminalValueType(typeName)) {
            return null;
        }
        if (isConstantLike(typeName)) {
            return null;
        }
        var resolved = sourceResolver.resolveType(session, context, typeName);
        if (!resolved.resolved()) {
            addUnresolvedType(state, typeName, context.path(), resolved);
            return resolved;
        }
        var effectiveRole = effectiveRole(session, resolved, preferredRole);
        state.addFile(
                resolved.filePath(),
                effectiveRole,
                symbol,
                reason,
                confidenceRank(confidence) >= confidenceRank(resolved.confidence()) ? confidence : resolved.confidence()
        );
        return resolved;
    }

    private boolean addSpringDataBoundary(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabJavaResolvedType repositoryType,
            String methodName,
            String fromSymbol
    ) {
        if (repositoryType == null || !repositoryType.resolved()) {
            return false;
        }
        var astFile = sourceResolver.astFile(session, repositoryType.filePath());
        if (!astFile.parsed()) {
            return false;
        }
        var detection = springDataRepositoryDetector.detect(
                astFile,
                repositoryType.qualifiedName() != null ? repositoryType.qualifiedName() : repositoryType.requestedName()
        );
        if (detection.status() != GitLabJavaSpringDataRepositoryStatus.DETECTED) {
            return false;
        }
        var repository = detection.repository();
        state.addFile(
                repository.filePath(),
                GitLabEndpointUseCaseFileRole.SPRING_DATA_REPOSITORY,
                methodName,
                "Spring Data repository is terminal generated bean.",
                repository.confidence()
        );
        state.addRelation(
                fromSymbol,
                symbol(repository.qualifiedName(), methodName),
                GitLabEndpointUseCaseRelationKind.SPRING_DATA_BOUNDARY,
                GitLabEndpointUseCaseConfidence.HIGH,
                "Repository extends " + repository.baseInterface() + "; generated implementation is not traversed."
        );
        if (StringUtils.hasText(repository.entityType())) {
            addResolvedType(
                    session,
                    state,
                    astFile,
                    repository.entityType(),
                    roleForTypeName(repository.entityType(), null),
                    repository.entityType(),
                    "Entity type used by Spring Data repository.",
                    GitLabEndpointUseCaseConfidence.MEDIUM
            );
        }
        return true;
    }

    private void addMapStructUses(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabJavaResolvedType mapperType,
            String currentSymbol
    ) {
        var astFile = sourceResolver.astFile(session, mapperType.filePath());
        if (!astFile.parsed()) {
            return;
        }
        var detection = mapStructResolver.detectMapper(
                astFile,
                mapperType.qualifiedName() != null ? mapperType.qualifiedName() : mapperType.requestedName()
        );
        if (detection.status() != GitLabJavaMapStructMapperStatus.DETECTED) {
            return;
        }
        for (var useType : detection.mapper().usesTypes()) {
            var resolvedUse = addResolvedType(
                    session,
                    state,
                    astFile,
                    useType,
                    GitLabEndpointUseCaseFileRole.MAPPER,
                    null,
                    "Mapper listed in MapStruct uses.",
                    GitLabEndpointUseCaseConfidence.MEDIUM
            );
            if (resolvedUse != null && resolvedUse.resolved()) {
                state.addRelation(
                        currentSymbol,
                        resolvedUse.qualifiedName() != null ? resolvedUse.qualifiedName() : useType,
                        GitLabEndpointUseCaseRelationKind.MAPPER_CALL,
                        GitLabEndpointUseCaseConfidence.MEDIUM,
                        "MapStruct @Mapper(uses = ...) dependency."
                );
            }
        }
    }

    private GitLabEndpointUseCaseFileRole effectiveRole(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaResolvedType resolved,
            GitLabEndpointUseCaseFileRole preferredRole
    ) {
        if (resolved == null || !resolved.resolved()) {
            return preferredRole != null ? preferredRole : GitLabEndpointUseCaseFileRole.UNKNOWN;
        }
        var astFile = sourceResolver.astFile(session, resolved.filePath());
        if (astFile.parsed()) {
            var typeName = resolved.qualifiedName() != null ? resolved.qualifiedName() : resolved.requestedName();
            if (mapStructResolver.detectMapper(astFile, typeName).status() == GitLabJavaMapStructMapperStatus.DETECTED) {
                return GitLabEndpointUseCaseFileRole.MAPPER;
            }
            if (springDataRepositoryDetector.detect(astFile, typeName).status()
                    == GitLabJavaSpringDataRepositoryStatus.DETECTED) {
                return GitLabEndpointUseCaseFileRole.SPRING_DATA_REPOSITORY;
            }
            if (isFeignClient(astFile, typeName)) {
                return GitLabEndpointUseCaseFileRole.EXTERNAL_CLIENT;
            }
        }
        if (preferredRole != null && preferredRole != GitLabEndpointUseCaseFileRole.UNKNOWN) {
            return preferredRole;
        }
        return roleForTypeName(resolved.qualifiedName() != null ? resolved.qualifiedName() : resolved.requestedName(),
                resolved.filePath());
    }

    private void addUnresolvedType(
            GitLabEndpointUseCaseTraversalState state,
            String typeName,
            String ownerPath,
            GitLabJavaResolvedType resolved
    ) {
        if (resolved != null && resolved.kind() == GitLabJavaTypeResolutionKind.EXTERNAL_BOUNDARY) {
            return;
        }
        state.addUnresolved(
                typeName,
                ownerPath,
                resolved != null && !resolved.limitations().isEmpty()
                        ? String.join(" ", resolved.limitations())
                        : "Type could not be resolved from current repository.",
                List.of(typeName),
                resolved != null ? resolved.candidates() : List.of()
        );
    }

    private void addMethodSignatureTypes(
            GitLabEndpointUseCaseSourceSession session,
            GitLabEndpointUseCaseTraversalState state,
            GitLabJavaAstFile astFile,
            GitLabJavaMethodMatch match
    ) {
        for (var parameterType : match.parameterTypes()) {
            addResolvedType(
                    session,
                    state,
                    astFile,
                    parameterType,
                    roleForTypeName(parameterType, null),
                    simpleName(parameterType),
                    "Method parameter type in traversed method.",
                    GitLabEndpointUseCaseConfidence.MEDIUM
            );
        }
        addResolvedType(
                session,
                state,
                astFile,
                match.returnType(),
                roleForTypeName(match.returnType(), null),
                simpleName(match.returnType()),
                "Method return type in traversed method.",
                GitLabEndpointUseCaseConfidence.MEDIUM
        );
    }

    private Map<String, GitLabJavaInjectedDependency> dependenciesByName(GitLabJavaDependencyModel dependencies) {
        var byName = new LinkedHashMap<String, GitLabJavaInjectedDependency>();
        for (var dependency : dependencies.injectedDependencies()) {
            byName.putIfAbsent(dependency.name(), dependency);
        }
        return byName;
    }

    private Map<String, String> localTypes(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaAstFile astFile,
            GitLabJavaMethodMatch currentMethod,
            Map<String, GitLabJavaInjectedDependency> dependenciesByName,
            MethodDeclaration method
    ) {
        var localTypes = new LinkedHashMap<String, String>();
        method.getParameters().forEach(parameter ->
                localTypes.put(parameter.getNameAsString(), parameter.getType().asString()));
        for (var declaration : method.findAll(VariableDeclarationExpr.class)) {
            var typeName = declaration.getElementType().asString();
            declaration.getVariables().forEach(variable -> {
                var inferredTypeName = "var".equals(typeName)
                        ? inferVariableType(session, astFile, currentMethod, dependenciesByName,
                        variable.getInitializer().orElse(null))
                        : typeName;
                if (StringUtils.hasText(inferredTypeName)) {
                    localTypes.putIfAbsent(variable.getNameAsString(), inferredTypeName);
                }
            });
        }
        return localTypes;
    }

    private String inferVariableType(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaAstFile astFile,
            GitLabJavaMethodMatch currentMethod,
            Map<String, GitLabJavaInjectedDependency> dependenciesByName,
            Expression initializer
    ) {
        if (initializer == null) {
            return null;
        }
        if (initializer.isEnclosedExpr()) {
            return inferVariableType(session, astFile, currentMethod, dependenciesByName,
                    initializer.asEnclosedExpr().getInner());
        }
        if (initializer.isCastExpr()) {
            return initializer.asCastExpr().getType().asString();
        }
        if (initializer.isObjectCreationExpr()) {
            return initializer.asObjectCreationExpr().getType().asString();
        }
        if (!initializer.isMethodCallExpr()) {
            return null;
        }

        var methodCall = initializer.asMethodCallExpr();
        var scope = methodCall.getScope().orElse(null);
        var scopeName = simpleScopeName(scope);
        if (StringUtils.hasText(scopeName)) {
            var dependency = dependenciesByName.get(scopeName);
            if (dependency != null) {
                return returnTypeFromResolvedMethod(
                        session,
                        astFile,
                        dependency.typeName(),
                        methodCall.getNameAsString(),
                        methodCall.getArguments().size()
                );
            }
        }
        if (scope == null || scope instanceof ThisExpr) {
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
        return null;
    }

    private String returnTypeFromResolvedMethod(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaAstFile context,
            String typeName,
            String methodName,
            int argumentCount
    ) {
        var resolvedType = sourceResolver.resolveType(session, context, typeName);
        if (!resolvedType.resolved()) {
            return null;
        }
        var targetAstFile = sourceResolver.astFile(session, resolvedType.filePath());
        if (!targetAstFile.parsed()) {
            return null;
        }
        var resolution = methodLocator.resolveMethod(
                targetAstFile,
                resolvedType.qualifiedName() != null ? resolvedType.qualifiedName() : typeName,
                methodName,
                argumentCount
        );
        return resolution.status() == GitLabJavaMethodResolutionStatus.RESOLVED
                ? resolution.method().returnType()
                : null;
    }

    private List<MethodCallExpr> directMethodCalls(MethodDeclaration method) {
        return method.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.findAncestor(MethodDeclaration.class).orElse(null) == method)
                .toList();
    }

    private List<ObjectCreationExpr> directObjectCreations(MethodDeclaration method) {
        return method.findAll(ObjectCreationExpr.class).stream()
                .filter(creation -> creation.findAncestor(MethodDeclaration.class).orElse(null) == method)
                .toList();
    }

    private List<String> addOpenApiContractFiles(
            GitLabEndpointUseCaseTraversalState state,
            GitLabEndpointUseCaseEndpointContext endpoint
    ) {
        var hints = new java.util.ArrayList<String>();
        hints.addAll(endpoint.annotations());
        hints.addAll(endpoint.suggestedNextReads());
        var paths = new java.util.LinkedHashSet<String>();
        for (var hint : hints) {
            var matcher = YAML_PATH_PATTERN.matcher(hint);
            while (matcher.find()) {
                var path = matcher.group(1);
                paths.add(path);
                state.addFile(
                        path,
                        GitLabEndpointUseCaseFileRole.OPENAPI_CONTRACT,
                        endpoint.path(),
                        "OpenAPI YAML contract linked by endpoint inventory.",
                        GitLabEndpointUseCaseConfidence.MEDIUM
                );
                state.addRelation(
                        endpoint.endpointId() != null ? endpoint.endpointId() : endpoint.path(),
                        path,
                        GitLabEndpointUseCaseRelationKind.OPENAPI_CONTRACT,
                        GitLabEndpointUseCaseConfidence.MEDIUM,
                        "Endpoint inventory pointed at OpenAPI YAML contract."
                );
            }
        }
        return paths.stream().toList();
    }

    private void addOpenApiGeneratedModelSymbols(
            GitLabEndpointUseCaseTraversalState state,
            GitLabEndpointUseCaseEndpointContext endpoint,
            List<String> openApiContractPaths
    ) {
        if (openApiContractPaths == null || openApiContractPaths.isEmpty()) {
            return;
        }
        var generatedTypes = generatedContractTypes(endpoint);
        if (generatedTypes.isEmpty()) {
            return;
        }
        for (var path : openApiContractPaths) {
            for (var generatedType : generatedTypes) {
                state.addFile(
                        path,
                        GitLabEndpointUseCaseFileRole.OPENAPI_CONTRACT,
                        generatedType,
                        "Generated request/response model declared by OpenAPI YAML contract.",
                        GitLabEndpointUseCaseConfidence.MEDIUM
                );
            }
        }
        state.addLimitation("Request/response web models are generated from OpenAPI YAML contract: "
                + String.join(", ", generatedTypes) + ".");
    }

    private List<String> generatedContractTypes(GitLabEndpointUseCaseEndpointContext endpoint) {
        var values = new java.util.ArrayList<String>();
        values.addAll(endpoint.requestTypes());
        values.addAll(endpoint.responseTypes());
        var result = new java.util.LinkedHashSet<String>();
        for (var value : values) {
            var matcher = TYPE_TOKEN_PATTERN.matcher(value);
            while (matcher.find()) {
                var token = matcher.group();
                if (!isTerminalValueType(token) && roleForTypeName(token, null) == GitLabEndpointUseCaseFileRole.WEB_MODEL) {
                    result.add(token);
                }
            }
        }
        return result.stream().toList();
    }

    private boolean isFeignClient(GitLabJavaAstFile astFile, String typeName) {
        var type = findType(astFile, typeName);
        return type != null && hasAnnotation(type, "FeignClient");
    }

    private TypeDeclaration<?> findType(GitLabJavaAstFile astFile, String typeName) {
        var simpleName = simpleName(typeName);
        return astFile.compilationUnit().findAll(TypeDeclaration.class).stream()
                .map(type -> (TypeDeclaration<?>) type)
                .filter(type -> simpleName.equals(type.getNameAsString())
                        || normalizeTypeName(typeName).endsWith("." + type.getNameAsString()))
                .findFirst()
                .orElse(null);
    }

    private String fieldTypeName(GitLabJavaAstFile astFile, String typeName, String fieldName) {
        var type = findType(astFile, typeName);
        if (type == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        return type.findAll(FieldDeclaration.class).stream()
                .filter(field -> field.findAncestor(TypeDeclaration.class).orElse(null) == type)
                .flatMap(field -> field.getVariables().stream())
                .filter(variable -> fieldName.equals(variable.getNameAsString()))
                .map(variable -> variable.getType().asString())
                .findFirst()
                .orElse(null);
    }

    private List<ConstructorDeclaration> immediateConstructors(TypeDeclaration<?> type) {
        return type.findAll(ConstructorDeclaration.class).stream()
                .filter(constructor -> constructor.findAncestor(TypeDeclaration.class).orElse(null) == type)
                .toList();
    }

    private List<MethodDeclaration> immediateMethods(TypeDeclaration<?> type) {
        return type.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.findAncestor(TypeDeclaration.class).orElse(null) == type)
                .toList();
    }

    private boolean hasAnnotation(NodeWithAnnotations<?> node, String annotationName) {
        return node.getAnnotations().stream()
                .map(annotation -> simpleName(annotation.getNameAsString()))
                .anyMatch(annotationName::equals);
    }

    private GitLabEndpointUseCaseFileRole roleForTypeName(String typeName, String filePath) {
        var normalized = normalizeTypeName(typeName);
        var simpleName = simpleName(normalized);
        var path = filePath != null ? filePath.toLowerCase() : "";
        if (!StringUtils.hasText(simpleName)) {
            return GitLabEndpointUseCaseFileRole.UNKNOWN;
        }
        if (simpleName.endsWith("Mapper") || simpleName.endsWith("Mapping")) {
            return GitLabEndpointUseCaseFileRole.MAPPER;
        }
        if (simpleName.endsWith("Api")) {
            return GitLabEndpointUseCaseFileRole.API_INTERFACE;
        }
        if (normalized.contains("RepositoryPort") || simpleName.contains("RepositoryPort")) {
            return GitLabEndpointUseCaseFileRole.REPOSITORY_PORT;
        }
        if (simpleName.endsWith("Port")) {
            return GitLabEndpointUseCaseFileRole.USE_CASE_PORT;
        }
        if (simpleName.endsWith("Service") || simpleName.endsWith("UseCase")) {
            return GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE;
        }
        if (simpleName.contains("Repository")) {
            return GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION;
        }
        if (simpleName.endsWith("Client")) {
            return GitLabEndpointUseCaseFileRole.EXTERNAL_CLIENT;
        }
        if (simpleName.contains("WebModel") || simpleName.endsWith("Dto")
                || simpleName.endsWith("Request") || simpleName.endsWith("Response")) {
            return GitLabEndpointUseCaseFileRole.WEB_MODEL;
        }
        if (simpleName.contains("FormView") || simpleName.endsWith("Projection") || simpleName.endsWith("View")) {
            return GitLabEndpointUseCaseFileRole.PROJECTION;
        }
        if (path.contains("/config/") || path.contains("/configuration/")) {
            return GitLabEndpointUseCaseFileRole.CONFIGURATION;
        }
        return GitLabEndpointUseCaseFileRole.DOMAIN_MODEL;
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

    private boolean isMapStructFactory(String scopeText) {
        return "Mappers".equals(scopeText) || "org.mapstruct.factory.Mappers".equals(scopeText);
    }

    private boolean isAccessorLike(String methodName) {
        return methodName != null
                && (methodName.startsWith("get") || methodName.startsWith("is") || methodName.startsWith("set"));
    }

    private boolean looksLikeTypeName(String value) {
        var normalized = normalizeTypeName(value);
        var simpleName = simpleName(normalized);
        return StringUtils.hasText(simpleName) && Character.isUpperCase(simpleName.charAt(0));
    }

    private boolean isConstantLike(String value) {
        var simpleName = simpleName(value);
        return StringUtils.hasText(simpleName)
                && simpleName.length() > 1
                && simpleName.equals(simpleName.toUpperCase(java.util.Locale.ROOT))
                && simpleName.matches("[A-Z][A-Z0-9_]*");
    }

    private boolean isTerminalValueType(String typeName) {
        var simpleName = simpleName(typeName);
        return simpleName == null || Set.of(
                "BigDecimal",
                "BigInteger",
                "Boolean",
                "Byte",
                "Character",
                "CharSequence",
                "Clock",
                "Collection",
                "Double",
                "Duration",
                "Float",
                "Instant",
                "Integer",
                "List",
                "LocalDate",
                "LocalDateTime",
                "Long",
                "Map",
                "Object",
                "Optional",
                "ResponseEntity",
                "Set",
                "Short",
                "String",
                "UUID",
                "Void",
                "boolean",
                "byte",
                "char",
                "double",
                "float",
                "int",
                "long",
                "short",
                "void"
        ).contains(simpleName);
    }

    private String symbol(String typeName, String methodName) {
        var normalizedTypeName = normalizeTypeName(typeName);
        var normalizedMethodName = GitLabEndpointUseCaseModelSupport.trimToNull(methodName);
        if (normalizedTypeName == null) {
            return normalizedMethodName;
        }
        if (normalizedMethodName == null) {
            return normalizedTypeName;
        }
        return normalizedTypeName + "#" + normalizedMethodName;
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
        var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(typeName);
        if (normalized == null) {
            return null;
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
        if (normalized.endsWith(".class")) {
            normalized = normalized.substring(0, normalized.length() - ".class".length());
        }
        var genericIndex = normalized.indexOf('<');
        if (genericIndex >= 0) {
            normalized = normalized.substring(0, genericIndex).trim();
        }
        return normalized.replace('$', '.');
    }

    private int confidenceRank(GitLabEndpointUseCaseConfidence confidence) {
        return switch (confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }
}
