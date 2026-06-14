package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
class GitLabEndpointUseCaseCallTargetResolver {

    GitLabEndpointUseCaseCallTargetResolution resolve(
            GitLabEndpointUseCaseCodeIndex codeIndex,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry,
            GitLabEndpointUseCaseDependencyResolution dependencyResolution
    ) {
        if (codeIndex == null || codeIndex.indexStatus() == GitLabEndpointUseCaseIndexStatus.NOT_BUILT) {
            return GitLabEndpointUseCaseCallTargetResolution.from(List.of(), codeIndex != null ? codeIndex.warnings() : List.of());
        }

        var context = new ResolverContext(codeIndex, beanRegistry, dependencyResolution);
        var warnings = new ArrayList<GitLabEndpointUseCaseWarning>(
                dependencyResolution != null ? dependencyResolution.warnings() : codeIndex.warnings());
        var resolvedCalls = new ArrayList<GitLabEndpointUseCaseResolvedCall>();

        for (var call : codeIndex.methodCallIndex().calls()) {
            var resolvedCall = resolveCall(call, context, warnings);
            context.registerResolvedCall(resolvedCall);
            resolvedCalls.add(resolvedCall);
        }

        return GitLabEndpointUseCaseCallTargetResolution.from(resolvedCalls, warnings);
    }

    private GitLabEndpointUseCaseResolvedCall resolveCall(
            GitLabEndpointUseCaseMethodCallInfo call,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var callerType = context.typeByMethodId().get(call.callerMethodId());
        var callerMethod = context.methodById().get(call.callerMethodId());
        if (callerType == null || callerMethod == null) {
            return unresolved(call, warnings, "Caller method is outside the indexed source snapshot.", List.of());
        }

        if (call.constructorCall()) {
            return resolveConstructorCall(call, context, warnings);
        }

        var receiver = normalizeReceiver(call.receiver());
        if (!StringUtils.hasText(receiver)) {
            return resolveMethodOnType(
                    call,
                    callerType,
                    callerType,
                    GitLabEndpointUseCaseResolutionKind.THIS_METHOD,
                    true,
                    context,
                    warnings
            );
        }
        if ("this".equals(receiver)) {
            return resolveMethodOnType(
                    call,
                    callerType,
                    callerType,
                    GitLabEndpointUseCaseResolutionKind.THIS_METHOD,
                    true,
                    context,
                    warnings
            );
        }
        if ("super".equals(receiver)) {
            return resolveSuperCall(call, callerType, context, warnings);
        }
        if (receiver.startsWith("this.")) {
            return resolveMemberReceiver(call, receiver.substring("this.".length()), callerType, context, warnings);
        }

        var dependency = context.dependencyResolution() != null
                ? context.dependencyResolution().findDependency(callerType.fqn(), receiver)
                : null;
        if (dependency != null && dependency.resolved()) {
            return resolveBeanCall(call, dependency, context, warnings);
        }

        var field = fieldByName(callerType, receiver);
        if (field != null) {
            return resolveTypedReceiver(
                    call,
                    field.type(),
                    receiver,
                    GitLabEndpointUseCaseResolutionKind.INHERITED_METHOD,
                    context,
                    warnings
            );
        }

        var parameter = parameterByName(callerMethod, receiver);
        if (parameter != null) {
            return resolveTypedReceiver(
                    call,
                    parameter.type(),
                    receiver,
                    GitLabEndpointUseCaseResolutionKind.DIRECT_METHOD,
                    context,
                    warnings
            );
        }

        var localVariable = localVariableByName(callerMethod, receiver);
        if (localVariable != null) {
            return resolveTypedReceiver(
                    call,
                    localVariable.type(),
                    receiver,
                    GitLabEndpointUseCaseResolutionKind.DIRECT_METHOD,
                    context,
                    warnings
            );
        }

        var singletonReceiverType = singletonReceiverType(receiver, context);
        if (singletonReceiverType != null) {
            return resolveMethodOnType(
                    call,
                    callerType,
                    singletonReceiverType,
                    GitLabEndpointUseCaseResolutionKind.STATIC_METHOD,
                    true,
                    context,
                    warnings
            );
        }

        var staticType = resolveType(receiver, context);
        if (staticType != null) {
            return resolveMethodOnType(
                    call,
                    callerType,
                    staticType,
                    GitLabEndpointUseCaseResolutionKind.STATIC_METHOD,
                    true,
                    context,
                    warnings
            );
        }

        if (looksExternalReceiver(receiver)) {
            return terminal(call, GitLabEndpointUseCaseResolutionKind.EXTERNAL_LIBRARY,
                    "Receiver is outside the indexed source snapshot: " + receiver + ".");
        }

        return unresolved(call, warnings, "Could not resolve receiver '" + receiver + "'.", List.of(receiver));
    }

    private GitLabEndpointUseCaseResolvedCall resolveConstructorCall(
            GitLabEndpointUseCaseMethodCallInfo call,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var targetType = resolveType(call.name(), context);
        if (targetType == null) {
            return terminal(call, GitLabEndpointUseCaseResolutionKind.EXTERNAL_LIBRARY,
                    "Constructed type is outside the indexed source snapshot: " + call.name() + ".");
        }
        if (call.argumentCount() == 0 && methodCandidates(targetType, "<init>", 0).isEmpty()) {
            return new GitLabEndpointUseCaseResolvedCall(
                    call,
                    GitLabEndpointUseCaseCallResolutionStatus.TERMINAL,
                    GitLabEndpointUseCaseResolutionKind.NEW_INSTANCE,
                    targetType.fqn(),
                    null,
                    null,
                    true,
                    "Default constructor has no indexed source body.",
                    List.of()
            );
        }
        return resolveMethodOnType(
                call,
                targetType,
                targetType,
                GitLabEndpointUseCaseResolutionKind.NEW_INSTANCE,
                false,
                context,
                warnings,
                "<init>"
        );
    }

    private GitLabEndpointUseCaseResolvedCall resolveSuperCall(
            GitLabEndpointUseCaseMethodCallInfo call,
            GitLabEndpointUseCaseTypeInfo callerType,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var parentTypes = parentTypes(callerType, context);
        var candidates = new ArrayList<GitLabEndpointUseCaseMethodInfo>();
        for (var parentType : parentTypes) {
            candidates.addAll(methodCandidates(parentType, call.name(), call.argumentCount()));
        }
        return resolvedCandidate(call, candidates, GitLabEndpointUseCaseResolutionKind.SUPER_METHOD, context, warnings);
    }

    private GitLabEndpointUseCaseResolvedCall resolveMemberReceiver(
            GitLabEndpointUseCaseMethodCallInfo call,
            String memberName,
            GitLabEndpointUseCaseTypeInfo callerType,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var dependency = context.dependencyResolution() != null
                ? context.dependencyResolution().findDependency(callerType.fqn(), memberName)
                : null;
        if (dependency != null && dependency.resolved()) {
            return resolveBeanCall(call, dependency, context, warnings);
        }
        var field = fieldByName(callerType, memberName);
        if (field != null) {
            return resolveTypedReceiver(
                    call,
                    field.type(),
                    memberName,
                    GitLabEndpointUseCaseResolutionKind.INHERITED_METHOD,
                    context,
                    warnings
            );
        }
        return unresolved(call, warnings, "Could not resolve member receiver '" + memberName + "'.", List.of(memberName));
    }

    private GitLabEndpointUseCaseResolvedCall resolveBeanCall(
            GitLabEndpointUseCaseMethodCallInfo call,
            GitLabEndpointUseCaseResolvedDependency dependency,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var targetType = resolveType(dependency.resolvedBean().type(), context);
        if (targetType == null) {
            return terminal(call, GitLabEndpointUseCaseResolutionKind.EXTERNAL_LIBRARY,
                    "Resolved bean type is outside the indexed source snapshot: " + dependency.resolvedBean().type() + ".");
        }
        var contractResolvedCall = resolveBeanCallThroughContract(call, dependency, targetType, context, warnings);
        if (contractResolvedCall != null) {
            return contractResolvedCall;
        }
        return resolveMethodOnType(
                call,
                targetType,
                targetType,
                dependency.resolutionKind(),
                true,
                context,
                warnings
        );
    }

    private GitLabEndpointUseCaseResolvedCall resolveBeanCallThroughContract(
            GitLabEndpointUseCaseMethodCallInfo call,
            GitLabEndpointUseCaseResolvedDependency dependency,
            GitLabEndpointUseCaseTypeInfo targetType,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var contractType = resolveType(dependency.injectionPoint().requiredType(), context);
        if (contractType == null || contractType.fqn().equals(targetType.fqn())) {
            return null;
        }

        var contractCandidates = narrowByArgumentTypes(
                call,
                deduplicateMethods(methodCandidates(contractType, call.name(), call.argumentCount())),
                context
        );
        if (contractCandidates.size() != 1) {
            return null;
        }

        var contractMethod = contractCandidates.get(0);
        var implementationCandidates = methodCandidates(targetType, call.name(), call.argumentCount()).stream()
                .filter(candidate -> sameErasedParameters(candidate, contractMethod))
                .toList();
        if (implementationCandidates.size() == 1) {
            return resolvedCall(call, implementationCandidates.get(0), dependency.resolutionKind());
        }
        if (implementationCandidates.size() > 1) {
            return ambiguous(call, warnings,
                    "Multiple implementation methods matched interface contract for call '" + call.expression() + "'.",
                    implementationCandidates.stream().map(GitLabEndpointUseCaseMethodInfo::id).toList());
        }
        return null;
    }

    private GitLabEndpointUseCaseResolvedCall resolveTypedReceiver(
            GitLabEndpointUseCaseMethodCallInfo call,
            String receiverType,
            String receiverName,
            GitLabEndpointUseCaseResolutionKind preferredResolutionKind,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var targetType = resolveType(receiverType, context);
        if (targetType != null) {
            var resolutionKind = targetType.kind() == GitLabEndpointUseCaseTypeKind.INTERFACE
                    ? GitLabEndpointUseCaseResolutionKind.INHERITED_METHOD
                    : preferredResolutionKind;
            if (targetType.kind() == GitLabEndpointUseCaseTypeKind.INTERFACE) {
                var beanResolvedCall = resolveInterfaceReceiverThroughRegistry(call, receiverType, receiverName, context, warnings);
                if (beanResolvedCall != null) {
                    return beanResolvedCall;
                }
            }
            return resolveMethodOnType(call, targetType, targetType, resolutionKind, true, context, warnings);
        }

        var beanResolvedCall = resolveInterfaceReceiverThroughRegistry(call, receiverType, receiverName, context, warnings);
        if (beanResolvedCall != null) {
            return beanResolvedCall;
        }

        return terminal(call, GitLabEndpointUseCaseResolutionKind.EXTERNAL_LIBRARY,
                "Receiver type is outside the indexed source snapshot: " + receiverType + ".");
    }

    private GitLabEndpointUseCaseResolvedCall resolveInterfaceReceiverThroughRegistry(
            GitLabEndpointUseCaseMethodCallInfo call,
            String receiverType,
            String receiverName,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        if (context.beanRegistry() == null) {
            return null;
        }

        var candidates = beanCandidates(receiverType, context.beanRegistry());
        if (candidates.isEmpty()) {
            return null;
        }

        var selected = selectBeanCandidate(candidates, receiverName);
        if (selected == null) {
            return ambiguous(call, warnings, "Multiple bean candidates found for receiver type " + receiverType + ".",
                    candidates.stream().map(bean -> bean.beanName() + ":" + bean.type()).toList());
        }

        var targetType = resolveType(selected.type(), context);
        if (targetType == null) {
            return terminal(call, GitLabEndpointUseCaseResolutionKind.EXTERNAL_LIBRARY,
                    "Selected bean type is outside the indexed source snapshot: " + selected.type() + ".");
        }
        return resolveMethodOnType(
                call,
                targetType,
                targetType,
                GitLabEndpointUseCaseResolutionKind.SPRING_BEAN_POLYMORPHIC,
                true,
                context,
                warnings
        );
    }

    private GitLabEndpointUseCaseResolvedCall resolveMethodOnType(
            GitLabEndpointUseCaseMethodCallInfo call,
            GitLabEndpointUseCaseTypeInfo callerType,
            GitLabEndpointUseCaseTypeInfo targetType,
            GitLabEndpointUseCaseResolutionKind resolutionKind,
            boolean includeInherited,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        return resolveMethodOnType(call, callerType, targetType, resolutionKind, includeInherited, context, warnings, call.name());
    }

    private GitLabEndpointUseCaseResolvedCall resolveMethodOnType(
            GitLabEndpointUseCaseMethodCallInfo call,
            GitLabEndpointUseCaseTypeInfo callerType,
            GitLabEndpointUseCaseTypeInfo targetType,
            GitLabEndpointUseCaseResolutionKind resolutionKind,
            boolean includeInherited,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings,
            String methodName
    ) {
        var candidates = new ArrayList<GitLabEndpointUseCaseMethodInfo>();
        candidates.addAll(methodCandidates(targetType, methodName, call.argumentCount()));
        var directCandidateFound = !candidates.isEmpty();
        if (includeInherited && !directCandidateFound) {
            for (var parentType : parentTypes(targetType, context)) {
                candidates.addAll(methodCandidates(parentType, methodName, call.argumentCount()));
            }
        }
        if (candidates.isEmpty()) {
            var lombokAccessor = lombokAccessor(targetType, call, includeInherited, context);
            if (lombokAccessor != null) {
                return terminal(
                        call,
                        resolutionKind,
                        lombokAccessor.declaringType().fqn(),
                        lombokAccessor.signature(),
                        lombokAccessor.reason()
                );
            }
        }

        var effectiveResolutionKind = resolutionKind;
        if (resolutionKind == GitLabEndpointUseCaseResolutionKind.THIS_METHOD
                && !directCandidateFound
                && candidates.stream().noneMatch(method -> method.id().startsWith(callerType.fqn() + "#"))) {
            effectiveResolutionKind = GitLabEndpointUseCaseResolutionKind.INHERITED_METHOD;
        }
        return resolvedCandidate(call, candidates, effectiveResolutionKind, context, warnings);
    }

    private GitLabEndpointUseCaseResolvedCall resolvedCandidate(
            GitLabEndpointUseCaseMethodCallInfo call,
            List<GitLabEndpointUseCaseMethodInfo> candidates,
            GitLabEndpointUseCaseResolutionKind resolutionKind,
            ResolverContext context,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var distinctCandidates = narrowByArgumentTypes(call, deduplicateMethods(candidates), context);
        if (distinctCandidates.size() == 1) {
            return resolvedCall(call, distinctCandidates.get(0), resolutionKind);
        }
        if (distinctCandidates.size() > 1) {
            return ambiguous(call, warnings, "Multiple method targets matched call '" + call.expression() + "'.",
                    distinctCandidates.stream().map(GitLabEndpointUseCaseMethodInfo::id).toList());
        }
        return unresolved(call, warnings, "No method target matched call '" + call.expression() + "'.", List.of(call.name()));
    }

    private GitLabEndpointUseCaseResolvedCall resolvedCall(
            GitLabEndpointUseCaseMethodCallInfo call,
            GitLabEndpointUseCaseMethodInfo method,
            GitLabEndpointUseCaseResolutionKind resolutionKind
    ) {
        return new GitLabEndpointUseCaseResolvedCall(
                call,
                GitLabEndpointUseCaseCallResolutionStatus.RESOLVED,
                resolutionKind,
                typeNameFromMethodId(method.id()),
                method.id(),
                method.signature(),
                false,
                null,
                List.of(method.id())
        );
    }

    private GitLabEndpointUseCaseResolvedCall unresolved(
            GitLabEndpointUseCaseMethodCallInfo call,
            List<GitLabEndpointUseCaseWarning> warnings,
            String message,
            List<String> candidates
    ) {
        warnings.add(new GitLabEndpointUseCaseWarning(
                GitLabEndpointUseCaseWarningCodes.CALL_TARGET_UNRESOLVED,
                GitLabEndpointUseCaseWarningSeverity.WARNING,
                message,
                call.sourcePath(),
                call.line(),
                candidates
        ));
        return new GitLabEndpointUseCaseResolvedCall(
                call,
                GitLabEndpointUseCaseCallResolutionStatus.UNRESOLVED,
                GitLabEndpointUseCaseResolutionKind.UNRESOLVED,
                null,
                null,
                null,
                false,
                null,
                candidates
        );
    }

    private GitLabEndpointUseCaseResolvedCall ambiguous(
            GitLabEndpointUseCaseMethodCallInfo call,
            List<GitLabEndpointUseCaseWarning> warnings,
            String message,
            List<String> candidates
    ) {
        var limitedCandidates = candidates.stream().limit(20).toList();
        warnings.add(new GitLabEndpointUseCaseWarning(
                GitLabEndpointUseCaseWarningCodes.CALL_TARGET_AMBIGUOUS,
                GitLabEndpointUseCaseWarningSeverity.WARNING,
                message,
                call.sourcePath(),
                call.line(),
                limitedCandidates
        ));
        return new GitLabEndpointUseCaseResolvedCall(
                call,
                GitLabEndpointUseCaseCallResolutionStatus.AMBIGUOUS,
                GitLabEndpointUseCaseResolutionKind.UNRESOLVED,
                null,
                null,
                null,
                false,
                null,
                limitedCandidates
        );
    }

    private GitLabEndpointUseCaseResolvedCall terminal(
            GitLabEndpointUseCaseMethodCallInfo call,
            GitLabEndpointUseCaseResolutionKind resolutionKind,
            String reason
    ) {
        return new GitLabEndpointUseCaseResolvedCall(
                call,
                GitLabEndpointUseCaseCallResolutionStatus.TERMINAL,
                resolutionKind,
                null,
                null,
                null,
                true,
                reason,
                List.of()
        );
    }

    private GitLabEndpointUseCaseResolvedCall terminal(
            GitLabEndpointUseCaseMethodCallInfo call,
            GitLabEndpointUseCaseResolutionKind resolutionKind,
            String targetType,
            String targetMethodSignature,
            String reason
    ) {
        return new GitLabEndpointUseCaseResolvedCall(
                call,
                GitLabEndpointUseCaseCallResolutionStatus.TERMINAL,
                resolutionKind,
                targetType,
                null,
                targetMethodSignature,
                true,
                reason,
                List.of()
        );
    }

    private List<GitLabEndpointUseCaseMethodInfo> methodCandidates(
            GitLabEndpointUseCaseTypeInfo type,
            String methodName,
            int argumentCount
    ) {
        return type.methods().stream()
                .filter(method -> method.name().equals(methodName))
                .filter(method -> method.parameters().size() == argumentCount)
                .toList();
    }

    private List<GitLabEndpointUseCaseMethodInfo> narrowByArgumentTypes(
            GitLabEndpointUseCaseMethodCallInfo call,
            List<GitLabEndpointUseCaseMethodInfo> candidates,
            ResolverContext context
    ) {
        if (candidates.size() <= 1 || call.arguments().size() != call.argumentCount()) {
            return candidates;
        }

        var callerMethod = context.methodById().get(call.callerMethodId());
        var callerType = context.typeByMethodId().get(call.callerMethodId());
        if (callerMethod == null || callerType == null) {
            return candidates;
        }

        var argumentTypes = call.arguments().stream()
                .map(argument -> inferExpressionType(argument, callerType, callerMethod, context, new LinkedHashSet<>()))
                .toList();
        if (argumentTypes.stream().noneMatch(StringUtils::hasText)) {
            return candidates;
        }

        var scoredCandidates = new ArrayList<ScoredMethodCandidate>();
        for (var candidate : candidates) {
            var score = overloadScore(candidate, argumentTypes, context);
            if (score > 0) {
                scoredCandidates.add(new ScoredMethodCandidate(candidate, score));
            }
        }
        if (scoredCandidates.isEmpty()) {
            return candidates;
        }

        var bestScore = scoredCandidates.stream()
                .mapToInt(ScoredMethodCandidate::score)
                .max()
                .orElse(0);
        return scoredCandidates.stream()
                .filter(candidate -> candidate.score() == bestScore)
                .map(ScoredMethodCandidate::method)
                .toList();
    }

    private int overloadScore(
            GitLabEndpointUseCaseMethodInfo candidate,
            List<String> argumentTypes,
            ResolverContext context
    ) {
        if (candidate.parameters().size() != argumentTypes.size()) {
            return 0;
        }

        var score = 0;
        var knownArguments = 0;
        for (var index = 0; index < candidate.parameters().size(); index++) {
            var argumentType = argumentTypes.get(index);
            if (!StringUtils.hasText(argumentType)) {
                continue;
            }
            knownArguments++;
            var parameterType = candidate.parameters().get(index).type();
            var matchScore = typeMatchScore(argumentType, parameterType, context);
            if (matchScore == 0) {
                return 0;
            }
            score += matchScore;
        }
        return knownArguments > 0 ? score : 0;
    }

    private int typeMatchScore(String actualType, String expectedType, ResolverContext context) {
        var actual = normalizeTypeName(actualType);
        var expected = normalizeTypeName(expectedType);
        if (!StringUtils.hasText(actual) || !StringUtils.hasText(expected)) {
            return 0;
        }
        if (sameErasedType(actual, expected)) {
            return 4;
        }
        if ("Object".equals(simpleName(expected))) {
            return 1;
        }
        if (isAssignableTo(actual, expected, context)) {
            return 2;
        }
        return 0;
    }

    private String inferExpressionType(
            String expression,
            GitLabEndpointUseCaseTypeInfo callerType,
            GitLabEndpointUseCaseMethodInfo callerMethod,
            ResolverContext context,
            LinkedHashSet<String> visitedExpressions
    ) {
        if (!StringUtils.hasText(expression) || !visitedExpressions.add(expression)) {
            return null;
        }

        var trimmed = expression.trim();
        if ("null".equals(trimmed)) {
            return null;
        }
        var castType = castType(trimmed);
        if (StringUtils.hasText(castType)) {
            return castType;
        }
        var constructedType = constructedType(trimmed);
        if (StringUtils.hasText(constructedType)) {
            return constructedType;
        }

        var resolvedCall = context.resolvedCall(callerMethod.id(), trimmed);
        if (resolvedCall != null && StringUtils.hasText(resolvedCall.targetMethodId())) {
            var targetMethod = context.methodById().get(resolvedCall.targetMethodId());
            if (targetMethod != null && StringUtils.hasText(targetMethod.returnType())) {
                return targetMethod.returnType();
            }
        }
        if (resolvedCall != null && isLombokGeneratedAccessor(resolvedCall)) {
            var syntheticReturnType = syntheticReturnType(resolvedCall.targetMethodSignature());
            if (StringUtils.hasText(syntheticReturnType)) {
                return syntheticReturnType;
            }
        }

        if (isSimpleIdentifier(trimmed)) {
            var parameter = parameterByName(callerMethod, trimmed);
            if (parameter != null) {
                return parameter.type();
            }
            var localVariable = localVariableByName(callerMethod, trimmed);
            if (localVariable != null) {
                if (StringUtils.hasText(localVariable.type()) && !"var".equals(localVariable.type())) {
                    return localVariable.type();
                }
                if (StringUtils.hasText(localVariable.initializer())) {
                    return inferExpressionType(
                            localVariable.initializer(),
                            callerType,
                            callerMethod,
                            context,
                            visitedExpressions
                    );
                }
            }
            var field = fieldByName(callerType, trimmed);
            if (field != null) {
                return field.type();
            }
        }

        return null;
    }

    private LombokAccessorMatch lombokAccessor(
            GitLabEndpointUseCaseTypeInfo targetType,
            GitLabEndpointUseCaseMethodCallInfo call,
            boolean includeInherited,
            ResolverContext context
    ) {
        var searchTypes = new ArrayList<GitLabEndpointUseCaseTypeInfo>();
        searchTypes.add(targetType);
        if (includeInherited) {
            searchTypes.addAll(parentTypes(targetType, context));
        }

        for (var candidateType : searchTypes) {
            var match = lombokAccessorOnType(candidateType, call);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private LombokAccessorMatch lombokAccessorOnType(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseMethodCallInfo call
    ) {
        if (call.argumentCount() == 0) {
            var getterFieldName = getterFieldName(call.name());
            if (StringUtils.hasText(getterFieldName)) {
                var field = fieldByLogicalName(type, getterFieldName);
                if (field != null && lombokGetterAvailable(type, field)) {
                    return lombokAccessorMatch(type, call.name() + "():" + field.type(),
                            "Lombok generated getter for field '" + field.name() + "'.");
                }
            }
        }

        if (call.argumentCount() == 1) {
            var setterFieldName = setterFieldName(call.name());
            if (StringUtils.hasText(setterFieldName)) {
                var field = fieldByLogicalName(type, setterFieldName);
                if (field != null && lombokSetterAvailable(type, field)) {
                    return lombokAccessorMatch(type, call.name() + "(" + field.type() + "):void",
                            "Lombok generated setter for field '" + field.name() + "'.");
                }
            }
        }

        return null;
    }

    private LombokAccessorMatch lombokAccessorMatch(
            GitLabEndpointUseCaseTypeInfo type,
            String signature,
            String reason
    ) {
        return new LombokAccessorMatch(type, signature, reason);
    }

    private GitLabEndpointUseCaseFieldInfo fieldByLogicalName(
            GitLabEndpointUseCaseTypeInfo type,
            String fieldName
    ) {
        return type.fields().stream()
                .filter(field -> field.name().equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    private boolean lombokGetterAvailable(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseFieldInfo field
    ) {
        return hasAnyAnnotation(type.annotationDetails(), "Getter", "Data", "Value")
                || hasAnyAnnotation(field.annotationDetails(), "Getter");
    }

    private boolean lombokSetterAvailable(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseFieldInfo field
    ) {
        if (field.finalField()) {
            return false;
        }
        return hasAnyAnnotation(type.annotationDetails(), "Setter", "Data")
                || hasAnyAnnotation(field.annotationDetails(), "Setter");
    }

    private boolean hasAnyAnnotation(
            List<GitLabEndpointUseCaseAnnotationInfo> annotations,
            String... names
    ) {
        if (annotations == null || names == null) {
            return false;
        }
        for (var annotation : annotations) {
            var annotationName = simpleName(annotation.name());
            for (var name : names) {
                if (name.equals(annotationName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getterFieldName(String methodName) {
        if (!StringUtils.hasText(methodName)) {
            return null;
        }
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return lowerFirst(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return lowerFirst(methodName.substring(2));
        }
        return null;
    }

    private String setterFieldName(String methodName) {
        if (StringUtils.hasText(methodName) && methodName.startsWith("set") && methodName.length() > 3) {
            return lowerFirst(methodName.substring(3));
        }
        return null;
    }

    private String lowerFirst(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.length() > 1 && Character.isUpperCase(value.charAt(0)) && Character.isUpperCase(value.charAt(1))) {
            return value;
        }
        return value.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + value.substring(1);
    }

    private boolean isLombokGeneratedAccessor(GitLabEndpointUseCaseResolvedCall resolvedCall) {
        return resolvedCall != null
                && StringUtils.hasText(resolvedCall.terminalReason())
                && resolvedCall.terminalReason().startsWith("Lombok generated ");
    }

    private String syntheticReturnType(String signature) {
        if (!StringUtils.hasText(signature)) {
            return null;
        }
        var separator = signature.lastIndexOf("):");
        return separator >= 0 ? signature.substring(separator + 2).trim() : null;
    }

    private boolean sameErasedParameters(
            GitLabEndpointUseCaseMethodInfo left,
            GitLabEndpointUseCaseMethodInfo right
    ) {
        if (left.parameters().size() != right.parameters().size()) {
            return false;
        }
        for (var index = 0; index < left.parameters().size(); index++) {
            if (!sameErasedType(left.parameters().get(index).type(), right.parameters().get(index).type())) {
                return false;
            }
        }
        return true;
    }

    private boolean sameErasedType(String left, String right) {
        var normalizedLeft = normalizeTypeName(left);
        var normalizedRight = normalizeTypeName(right);
        return StringUtils.hasText(normalizedLeft)
                && StringUtils.hasText(normalizedRight)
                && (normalizedLeft.equals(normalizedRight)
                || simpleName(normalizedLeft).equals(simpleName(normalizedRight)));
    }

    private boolean isAssignableTo(String actualType, String expectedType, ResolverContext context) {
        var actual = resolveType(actualType, context);
        if (actual == null) {
            return false;
        }
        var expected = normalizeTypeName(expectedType);
        var queue = new ArrayDeque<String>(actual.directParentTypes());
        var visited = new LinkedHashSet<String>();
        while (!queue.isEmpty()) {
            var parentName = normalizeTypeName(queue.removeFirst());
            if (!StringUtils.hasText(parentName) || !visited.add(parentName)) {
                continue;
            }
            if (sameErasedType(parentName, expected)) {
                return true;
            }
            var parentType = resolveType(parentName, context);
            if (parentType != null) {
                queue.addAll(parentType.directParentTypes());
            }
        }
        return false;
    }

    private String castType(String expression) {
        if (!expression.startsWith("(")) {
            return null;
        }
        var closingIndex = expression.indexOf(')');
        if (closingIndex <= 1) {
            return null;
        }
        var candidate = expression.substring(1, closingIndex).trim();
        return isLikelyTypeName(candidate) ? candidate : null;
    }

    private String constructedType(String expression) {
        if (!expression.startsWith("new ")) {
            return null;
        }
        var rest = expression.substring("new ".length()).trim();
        var parenthesisIndex = rest.indexOf('(');
        return parenthesisIndex > 0 ? rest.substring(0, parenthesisIndex).trim() : null;
    }

    private boolean isSimpleIdentifier(String value) {
        if (!StringUtils.hasText(value) || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (var index = 1; index < value.length(); index++) {
            if (!Character.isJavaIdentifierPart(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean isLikelyTypeName(String value) {
        return StringUtils.hasText(value)
                && Character.isJavaIdentifierStart(value.charAt(0))
                && Character.isUpperCase(value.charAt(0));
    }

    private List<GitLabEndpointUseCaseTypeInfo> parentTypes(
            GitLabEndpointUseCaseTypeInfo type,
            ResolverContext context
    ) {
        var result = new ArrayList<GitLabEndpointUseCaseTypeInfo>();
        var queue = new ArrayDeque<String>(type.directParentTypes());
        var visited = new LinkedHashSet<String>();

        while (!queue.isEmpty()) {
            var parentName = queue.removeFirst();
            if (!visited.add(parentName)) {
                continue;
            }
            var parentType = resolveType(parentName, context);
            if (parentType == null) {
                continue;
            }
            result.add(parentType);
            queue.addAll(parentType.directParentTypes());
        }

        return List.copyOf(result);
    }

    private List<GitLabEndpointUseCaseMethodInfo> deduplicateMethods(
            List<GitLabEndpointUseCaseMethodInfo> methods
    ) {
        var byId = new LinkedHashMap<String, GitLabEndpointUseCaseMethodInfo>();
        for (var method : methods) {
            byId.putIfAbsent(method.id(), method);
        }
        return List.copyOf(byId.values());
    }

    private GitLabEndpointUseCaseTypeInfo resolveType(String typeName, ResolverContext context) {
        var normalizedTypeName = normalizeTypeName(typeName);
        if (!StringUtils.hasText(normalizedTypeName)) {
            return null;
        }
        var byFqn = context.codeIndex().typesByFqn().get(normalizedTypeName);
        if (byFqn != null) {
            return byFqn;
        }
        var bySimpleName = context.codeIndex().typesBySimpleName().get(simpleName(normalizedTypeName));
        if (bySimpleName != null && bySimpleName.size() == 1) {
            return bySimpleName.get(0);
        }
        return uniqueSuffixMatch(normalizedTypeName, context);
    }

    private GitLabEndpointUseCaseTypeInfo singletonReceiverType(
            String receiver,
            ResolverContext context
    ) {
        if (!StringUtils.hasText(receiver) || !receiver.endsWith(".INSTANCE")) {
            return null;
        }
        var typeName = receiver.substring(0, receiver.length() - ".INSTANCE".length());
        return resolveType(typeName, context);
    }

    private GitLabEndpointUseCaseTypeInfo uniqueSuffixMatch(
            String typeName,
            ResolverContext context
    ) {
        var suffix = "." + typeName;
        var matches = context.codeIndex().types().stream()
                .filter(type -> type.fqn().endsWith(suffix))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private List<GitLabEndpointUseCaseSpringBean> beanCandidates(
            String typeName,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry
    ) {
        var candidates = new LinkedHashSet<GitLabEndpointUseCaseSpringBean>();
        var normalizedType = normalizeTypeName(typeName);
        if (StringUtils.hasText(normalizedType)) {
            candidates.addAll(beanRegistry.candidatesForType(normalizedType));
            candidates.addAll(beanRegistry.candidatesForType(simpleName(normalizedType)));
        }
        return List.copyOf(candidates);
    }

    private GitLabEndpointUseCaseSpringBean selectBeanCandidate(
            List<GitLabEndpointUseCaseSpringBean> candidates,
            String receiverName
    ) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        var primaryCandidates = candidates.stream()
                .filter(GitLabEndpointUseCaseSpringBean::primary)
                .toList();
        if (primaryCandidates.size() == 1) {
            return primaryCandidates.get(0);
        }
        var nameCandidates = candidates.stream()
                .filter(bean -> receiverName.equals(bean.beanName()) || bean.aliases().contains(receiverName))
                .toList();
        return nameCandidates.size() == 1 ? nameCandidates.get(0) : null;
    }

    private GitLabEndpointUseCaseFieldInfo fieldByName(GitLabEndpointUseCaseTypeInfo type, String fieldName) {
        for (var candidateType : fieldSearchTypes(type)) {
            for (var field : candidateType.fields()) {
                if (field.name().equals(fieldName)) {
                    return field;
                }
            }
        }
        return null;
    }

    private List<GitLabEndpointUseCaseTypeInfo> fieldSearchTypes(GitLabEndpointUseCaseTypeInfo type) {
        var types = new ArrayList<GitLabEndpointUseCaseTypeInfo>();
        types.add(type);
        return List.copyOf(types);
    }

    private GitLabEndpointUseCaseParameterInfo parameterByName(
            GitLabEndpointUseCaseMethodInfo method,
            String parameterName
    ) {
        return method.parameters().stream()
                .filter(parameter -> parameter.name().equals(parameterName))
                .findFirst()
                .orElse(null);
    }

    private GitLabEndpointUseCaseLocalVariableInfo localVariableByName(
            GitLabEndpointUseCaseMethodInfo method,
            String variableName
    ) {
        return method.localVariables().stream()
                .filter(variable -> variable.name().equals(variableName))
                .findFirst()
                .orElse(null);
    }

    private String normalizeReceiver(String receiver) {
        return StringUtils.hasText(receiver) ? receiver.trim() : null;
    }

    private boolean looksExternalReceiver(String receiver) {
        return StringUtils.hasText(receiver)
                && (Character.isUpperCase(receiver.charAt(0)) || receiver.contains("."));
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

    private String typeNameFromMethodId(String methodId) {
        var separator = methodId.indexOf('#');
        return separator >= 0 ? methodId.substring(0, separator) : null;
    }

    private record ScoredMethodCandidate(GitLabEndpointUseCaseMethodInfo method, int score) {
    }

    private record LombokAccessorMatch(
            GitLabEndpointUseCaseTypeInfo declaringType,
            String signature,
            String reason
    ) {
    }

    private record ResolverContext(
            GitLabEndpointUseCaseCodeIndex codeIndex,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry,
            GitLabEndpointUseCaseDependencyResolution dependencyResolution,
            Map<String, GitLabEndpointUseCaseTypeInfo> typeByMethodId,
            Map<String, GitLabEndpointUseCaseMethodInfo> methodById,
            Map<String, GitLabEndpointUseCaseResolvedCall> resolvedCallsByExpression
    ) {
        private ResolverContext(
                GitLabEndpointUseCaseCodeIndex codeIndex,
                GitLabEndpointUseCaseSpringBeanRegistry beanRegistry,
                GitLabEndpointUseCaseDependencyResolution dependencyResolution
        ) {
            this(
                    codeIndex,
                    beanRegistry,
                    dependencyResolution,
                    typeByMethodId(codeIndex),
                    methodById(codeIndex),
                    new LinkedHashMap<>()
            );
        }

        private void registerResolvedCall(GitLabEndpointUseCaseResolvedCall resolvedCall) {
            if (resolvedCall == null || resolvedCall.call() == null) {
                return;
            }
            resolvedCallsByExpression.putIfAbsent(
                    callKey(resolvedCall.call().callerMethodId(), resolvedCall.call().expression()),
                    resolvedCall
            );
        }

        private GitLabEndpointUseCaseResolvedCall resolvedCall(String callerMethodId, String expression) {
            return resolvedCallsByExpression.get(callKey(callerMethodId, expression));
        }

        private static String callKey(String callerMethodId, String expression) {
            return (callerMethodId != null ? callerMethodId : "") + "|" + (expression != null ? expression : "");
        }

        private static Map<String, GitLabEndpointUseCaseTypeInfo> typeByMethodId(
                GitLabEndpointUseCaseCodeIndex codeIndex
        ) {
            var map = new LinkedHashMap<String, GitLabEndpointUseCaseTypeInfo>();
            for (var type : codeIndex.types()) {
                for (var method : type.methods()) {
                    map.put(method.id(), type);
                }
            }
            return Map.copyOf(map);
        }

        private static Map<String, GitLabEndpointUseCaseMethodInfo> methodById(
                GitLabEndpointUseCaseCodeIndex codeIndex
        ) {
            var map = new LinkedHashMap<String, GitLabEndpointUseCaseMethodInfo>();
            for (var type : codeIndex.types()) {
                for (var method : type.methods()) {
                    map.put(method.id(), method);
                }
            }
            return Map.copyOf(map);
        }
    }
}
