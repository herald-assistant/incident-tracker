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
            resolvedCalls.add(resolveCall(call, context, warnings));
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
        return resolvedCandidate(call, candidates, GitLabEndpointUseCaseResolutionKind.SUPER_METHOD, warnings);
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

        var effectiveResolutionKind = resolutionKind;
        if (resolutionKind == GitLabEndpointUseCaseResolutionKind.THIS_METHOD
                && !directCandidateFound
                && candidates.stream().noneMatch(method -> method.id().startsWith(callerType.fqn() + "#"))) {
            effectiveResolutionKind = GitLabEndpointUseCaseResolutionKind.INHERITED_METHOD;
        }
        return resolvedCandidate(call, candidates, effectiveResolutionKind, warnings);
    }

    private GitLabEndpointUseCaseResolvedCall resolvedCandidate(
            GitLabEndpointUseCaseMethodCallInfo call,
            List<GitLabEndpointUseCaseMethodInfo> candidates,
            GitLabEndpointUseCaseResolutionKind resolutionKind,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var distinctCandidates = deduplicateMethods(candidates);
        if (distinctCandidates.size() == 1) {
            var method = distinctCandidates.get(0);
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
        if (distinctCandidates.size() > 1) {
            return ambiguous(call, warnings, "Multiple method targets matched call '" + call.expression() + "'.",
                    distinctCandidates.stream().map(GitLabEndpointUseCaseMethodInfo::id).toList());
        }
        return unresolved(call, warnings, "No method target matched call '" + call.expression() + "'.", List.of(call.name()));
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
        return bySimpleName != null && bySimpleName.size() == 1 ? bySimpleName.get(0) : null;
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

    private record ResolverContext(
            GitLabEndpointUseCaseCodeIndex codeIndex,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry,
            GitLabEndpointUseCaseDependencyResolution dependencyResolution,
            Map<String, GitLabEndpointUseCaseTypeInfo> typeByMethodId,
            Map<String, GitLabEndpointUseCaseMethodInfo> methodById
    ) {
        private ResolverContext(
                GitLabEndpointUseCaseCodeIndex codeIndex,
                GitLabEndpointUseCaseSpringBeanRegistry beanRegistry,
                GitLabEndpointUseCaseDependencyResolution dependencyResolution
        ) {
            this(codeIndex, beanRegistry, dependencyResolution, typeByMethodId(codeIndex), methodById(codeIndex));
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
