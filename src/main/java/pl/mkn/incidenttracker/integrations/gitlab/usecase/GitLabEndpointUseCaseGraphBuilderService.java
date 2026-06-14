package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
class GitLabEndpointUseCaseGraphBuilderService {

    GitLabEndpointUseCaseGraphBuildResult buildGraph(
            GitLabEndpointUseCaseEndpointCandidate endpoint,
            GitLabEndpointUseCaseCodeIndex codeIndex,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry,
            GitLabEndpointUseCaseCallTargetResolution callTargetResolution,
            int maxDepth,
            int maxNodes
    ) {
        var effectiveMaxDepth = maxDepth > 0 ? maxDepth : GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_DEPTH;
        var effectiveMaxNodes = maxNodes > 0 ? maxNodes : GitLabEndpointUseCaseContextRequest.DEFAULT_MAX_NODES;
        var warnings = new ArrayList<GitLabEndpointUseCaseWarning>(
                callTargetResolution != null ? callTargetResolution.warnings() : List.of());

        if (endpoint == null || codeIndex == null || codeIndex.indexStatus() == GitLabEndpointUseCaseIndexStatus.NOT_BUILT) {
            return new GitLabEndpointUseCaseGraphBuildResult(
                    GitLabEndpointUseCaseGraph.empty(),
                    new GitLabEndpointUseCaseLimits(effectiveMaxDepth, effectiveMaxNodes, false, false),
                    warnings
            );
        }

        var context = new GraphContext(codeIndex, beanRegistry, callTargetResolution);
        var rootMethod = context.methodById().get(endpoint.controllerMethodId());
        var rootType = context.typeByMethodId().get(endpoint.controllerMethodId());
        if (rootMethod == null || rootType == null) {
            warnings.add(new GitLabEndpointUseCaseWarning(
                    GitLabEndpointUseCaseWarningCodes.CALL_TARGET_UNRESOLVED,
                    GitLabEndpointUseCaseWarningSeverity.WARNING,
                    "Endpoint handler method is outside the indexed source snapshot.",
                    endpoint.sourcePath(),
                    endpoint.lineStart(),
                    List.of(endpoint.controllerMethodId())
            ));
            return new GitLabEndpointUseCaseGraphBuildResult(
                    GitLabEndpointUseCaseGraph.empty(),
                    new GitLabEndpointUseCaseLimits(effectiveMaxDepth, effectiveMaxNodes, false, false),
                    warnings
            );
        }

        var nodes = new LinkedHashMap<String, GitLabEndpointUseCaseNode>();
        var edges = new ArrayList<GitLabEndpointUseCaseEdge>();
        var queue = new ArrayDeque<TraversalItem>();
        var expanded = new LinkedHashSet<String>();
        var scheduled = new LinkedHashSet<String>();
        var depthWarnings = new LinkedHashSet<String>();
        var cycleWarnings = new LinkedHashSet<String>();
        var maxDepthReached = false;
        var maxNodesReached = false;

        nodes.put(endpoint.controllerMethodId(), nodeForMethod(endpoint.controllerMethodId(), rootType, rootMethod,
                GitLabEndpointUseCaseRole.CONTROLLER, 0, false, null));
        queue.add(new TraversalItem(endpoint.controllerMethodId(), 0, List.of(endpoint.controllerMethodId())));
        scheduled.add(endpoint.controllerMethodId());

        while (!queue.isEmpty() && !maxNodesReached) {
            var item = queue.removeFirst();
            if (!expanded.add(item.methodId())) {
                continue;
            }
            if (item.depth() >= effectiveMaxDepth) {
                maxDepthReached = true;
                addDepthWarning(item, context, warnings, depthWarnings);
                continue;
            }

            var calls = context.callsByCaller().getOrDefault(item.methodId(), List.of());
            for (var resolvedCall : calls) {
                if (resolvedCall.resolved()) {
                    var appendResult = appendResolvedCall(
                            item,
                            resolvedCall,
                            context,
                            nodes,
                            edges,
                            queue,
                            scheduled,
                            cycleWarnings,
                            warnings,
                            effectiveMaxNodes
                    );
                    maxNodesReached = appendResult.maxNodesReached();
                    if (maxNodesReached) {
                        addMaxNodesWarning(resolvedCall.call(), warnings);
                        break;
                    }
                    if (appendResult.cycleDetected()) {
                        addCycleWarning(item, resolvedCall, warnings, cycleWarnings);
                    }
                    continue;
                }

                if (nodes.size() >= effectiveMaxNodes) {
                    maxNodesReached = true;
                    addMaxNodesWarning(resolvedCall.call(), warnings);
                    break;
                }
                var terminalNode = terminalNode(item, resolvedCall);
                nodes.put(terminalNode.id(), terminalNode);
                edges.add(edge(item.methodId(), terminalNode.id(), resolvedCall, terminalNode.role()));
            }
        }

        return new GitLabEndpointUseCaseGraphBuildResult(
                new GitLabEndpointUseCaseGraph(List.copyOf(nodes.values()), edges),
                new GitLabEndpointUseCaseLimits(effectiveMaxDepth, effectiveMaxNodes, maxDepthReached, maxNodesReached),
                warnings
        );
    }

    private AppendCallResult appendResolvedCall(
            TraversalItem item,
            GitLabEndpointUseCaseResolvedCall resolvedCall,
            GraphContext context,
            Map<String, GitLabEndpointUseCaseNode> nodes,
            List<GitLabEndpointUseCaseEdge> edges,
            ArrayDeque<TraversalItem> queue,
            Set<String> scheduled,
            Set<String> cycleWarnings,
            List<GitLabEndpointUseCaseWarning> warnings,
            int maxNodes
    ) {
        var targetMethod = context.methodById().get(resolvedCall.targetMethodId());
        var targetType = context.typeByMethodId().get(resolvedCall.targetMethodId());
        if (targetMethod == null || targetType == null) {
            var fallback = terminalNode(item, resolvedCall,
                    "Resolved target method is outside the indexed source snapshot.");
            if (nodes.size() >= maxNodes) {
                return new AppendCallResult(true, false);
            }
            nodes.put(fallback.id(), fallback);
            edges.add(edge(item.methodId(), fallback.id(), resolvedCall, fallback.role()));
            return new AppendCallResult(false, false);
        }

        var targetRole = roleForType(targetType, context.beanRegistry());
        var terminalReason = terminalReason(targetType, targetRole, context.beanRegistry(), resolvedCall.call());
        var terminal = StringUtils.hasText(terminalReason);
        if (!nodes.containsKey(resolvedCall.targetMethodId())) {
            if (nodes.size() >= maxNodes) {
                return new AppendCallResult(true, false);
            }
            nodes.put(resolvedCall.targetMethodId(), nodeForMethod(
                    resolvedCall.targetMethodId(),
                    targetType,
                    targetMethod,
                    targetRole,
                    item.depth() + 1,
                    terminal,
                    terminalReason
            ));
        }

        edges.add(edge(item.methodId(), resolvedCall.targetMethodId(), resolvedCall, targetRole));

        if (terminal) {
            return new AppendCallResult(false, false);
        }
        if (item.path().contains(resolvedCall.targetMethodId())) {
            return new AppendCallResult(false, cycleWarnings.add(item.methodId() + "->" + resolvedCall.targetMethodId()));
        }
        if (scheduled.add(resolvedCall.targetMethodId())) {
            queue.add(new TraversalItem(
                    resolvedCall.targetMethodId(),
                    item.depth() + 1,
                    appendPath(item.path(), resolvedCall.targetMethodId())
            ));
        }
        return new AppendCallResult(false, false);
    }

    private GitLabEndpointUseCaseNode nodeForMethod(
            String methodId,
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseMethodInfo method,
            GitLabEndpointUseCaseRole role,
            int depth,
            boolean terminal,
            String terminalReason
    ) {
        return new GitLabEndpointUseCaseNode(
                methodId,
                GitLabEndpointUseCaseNodeKind.METHOD,
                type.fqn(),
                method.signature(),
                role,
                depth,
                type.sourcePath(),
                line(method.lineStart(), type.lineStart()),
                line(method.lineEnd(), type.lineEnd()),
                terminal,
                terminalReason
        );
    }

    private GitLabEndpointUseCaseNode terminalNode(
            TraversalItem item,
            GitLabEndpointUseCaseResolvedCall resolvedCall
    ) {
        return terminalNode(item, resolvedCall, terminalReason(resolvedCall));
    }

    private GitLabEndpointUseCaseNode terminalNode(
            TraversalItem item,
            GitLabEndpointUseCaseResolvedCall resolvedCall,
            String reason
    ) {
        var call = resolvedCall.call();
        var nodeKind = terminalNodeKind(resolvedCall);
        var role = terminalRole(resolvedCall);
        return new GitLabEndpointUseCaseNode(
                terminalNodeId(item.methodId(), call),
                nodeKind,
                terminalClassName(resolvedCall),
                call.expression(),
                role,
                item.depth() + 1,
                call.sourcePath(),
                line(call.line(), null),
                line(call.line(), null),
                true,
                reason
        );
    }

    private GitLabEndpointUseCaseEdge edge(
            String from,
            String to,
            GitLabEndpointUseCaseResolvedCall resolvedCall,
            GitLabEndpointUseCaseRole targetRole
    ) {
        return new GitLabEndpointUseCaseEdge(
                from,
                to,
                edgeKind(resolvedCall, targetRole),
                resolvedCall.resolutionKind(),
                resolvedCall.call().expression(),
                resolvedCall.call().line(),
                confidence(resolvedCall),
                resolvedCall.status() == GitLabEndpointUseCaseCallResolutionStatus.AMBIGUOUS
        );
    }

    private GitLabEndpointUseCaseEdgeKind edgeKind(
            GitLabEndpointUseCaseResolvedCall resolvedCall,
            GitLabEndpointUseCaseRole targetRole
    ) {
        if (resolvedCall.status() == GitLabEndpointUseCaseCallResolutionStatus.UNRESOLVED
                || resolvedCall.status() == GitLabEndpointUseCaseCallResolutionStatus.AMBIGUOUS) {
            return GitLabEndpointUseCaseEdgeKind.UNRESOLVED_CALL;
        }
        if (isEventPublishCall(resolvedCall.call()) || targetRole == GitLabEndpointUseCaseRole.EVENT_PUBLISHER) {
            return GitLabEndpointUseCaseEdgeKind.EVENT_PUBLISH;
        }
        if (targetRole == GitLabEndpointUseCaseRole.REPOSITORY) {
            return repositoryEdgeKind(resolvedCall.call().name());
        }
        if (targetRole == GitLabEndpointUseCaseRole.EXTERNAL_CLIENT
                || resolvedCall.resolutionKind() == GitLabEndpointUseCaseResolutionKind.EXTERNAL_LIBRARY) {
            return GitLabEndpointUseCaseEdgeKind.EXTERNAL_CALL;
        }
        if (targetRole == GitLabEndpointUseCaseRole.MAPPER || containsIgnoreCase(resolvedCall.call().name(), "map")) {
            return GitLabEndpointUseCaseEdgeKind.MAPPING;
        }
        if (targetRole == GitLabEndpointUseCaseRole.VALIDATOR || containsIgnoreCase(resolvedCall.call().name(), "valid")) {
            return GitLabEndpointUseCaseEdgeKind.VALIDATION;
        }
        if (resolvedCall.resolutionKind() == GitLabEndpointUseCaseResolutionKind.SUPER_METHOD
                || resolvedCall.resolutionKind() == GitLabEndpointUseCaseResolutionKind.INHERITED_METHOD) {
            return GitLabEndpointUseCaseEdgeKind.INHERITANCE_CALL;
        }
        return GitLabEndpointUseCaseEdgeKind.SYNC_CALL;
    }

    private GitLabEndpointUseCaseEdgeKind repositoryEdgeKind(String methodName) {
        var name = lower(methodName);
        if (name.startsWith("save")
                || name.startsWith("delete")
                || name.startsWith("update")
                || name.startsWith("insert")
                || name.startsWith("persist")
                || name.startsWith("merge")) {
            return GitLabEndpointUseCaseEdgeKind.REPOSITORY_WRITE;
        }
        return GitLabEndpointUseCaseEdgeKind.REPOSITORY_READ;
    }

    private GitLabEndpointUseCaseConfidence confidence(GitLabEndpointUseCaseResolvedCall resolvedCall) {
        if (resolvedCall.status() == GitLabEndpointUseCaseCallResolutionStatus.RESOLVED) {
            return GitLabEndpointUseCaseConfidence.HIGH;
        }
        if (resolvedCall.status() == GitLabEndpointUseCaseCallResolutionStatus.TERMINAL
                && resolvedCall.resolutionKind() != GitLabEndpointUseCaseResolutionKind.UNRESOLVED) {
            return GitLabEndpointUseCaseConfidence.MEDIUM;
        }
        return GitLabEndpointUseCaseConfidence.LOW;
    }

    private String terminalReason(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseRole role,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry,
            GitLabEndpointUseCaseMethodCallInfo call
    ) {
        if (role == GitLabEndpointUseCaseRole.REPOSITORY) {
            return "Repository boundary.";
        }
        if (role == GitLabEndpointUseCaseRole.EXTERNAL_CLIENT) {
            return "External client boundary.";
        }
        if (role == GitLabEndpointUseCaseRole.EVENT_PUBLISHER || isEventPublishCall(call)) {
            return "Event publication boundary.";
        }
        if (role == GitLabEndpointUseCaseRole.MAPPER
                && type.kind() == GitLabEndpointUseCaseTypeKind.INTERFACE
                && beanForType(type.fqn(), beanRegistry) != null) {
            return "Mapper interface boundary; generated implementation is not indexed.";
        }
        return null;
    }

    private String terminalReason(GitLabEndpointUseCaseResolvedCall resolvedCall) {
        if (StringUtils.hasText(resolvedCall.terminalReason())) {
            return resolvedCall.terminalReason();
        }
        if (resolvedCall.status() == GitLabEndpointUseCaseCallResolutionStatus.AMBIGUOUS) {
            return "Call target is ambiguous.";
        }
        if (resolvedCall.status() == GitLabEndpointUseCaseCallResolutionStatus.UNRESOLVED) {
            return "Call target could not be resolved.";
        }
        if (resolvedCall.resolutionKind() == GitLabEndpointUseCaseResolutionKind.EXTERNAL_LIBRARY) {
            return "External library boundary.";
        }
        return "Terminal call boundary.";
    }

    private GitLabEndpointUseCaseNodeKind terminalNodeKind(GitLabEndpointUseCaseResolvedCall resolvedCall) {
        if (resolvedCall.status() == GitLabEndpointUseCaseCallResolutionStatus.UNRESOLVED
                || resolvedCall.status() == GitLabEndpointUseCaseCallResolutionStatus.AMBIGUOUS) {
            return GitLabEndpointUseCaseNodeKind.UNRESOLVED;
        }
        if (resolvedCall.resolutionKind() == GitLabEndpointUseCaseResolutionKind.EXTERNAL_LIBRARY) {
            return GitLabEndpointUseCaseNodeKind.EXTERNAL;
        }
        return GitLabEndpointUseCaseNodeKind.TERMINAL;
    }

    private GitLabEndpointUseCaseRole terminalRole(GitLabEndpointUseCaseResolvedCall resolvedCall) {
        if (resolvedCall.resolutionKind() == GitLabEndpointUseCaseResolutionKind.EXTERNAL_LIBRARY) {
            return GitLabEndpointUseCaseRole.EXTERNAL_CLIENT;
        }
        if (isEventPublishCall(resolvedCall.call())) {
            return GitLabEndpointUseCaseRole.EVENT_PUBLISHER;
        }
        return GitLabEndpointUseCaseRole.UNKNOWN;
    }

    private String terminalClassName(GitLabEndpointUseCaseResolvedCall resolvedCall) {
        if (StringUtils.hasText(resolvedCall.targetType())) {
            return resolvedCall.targetType();
        }
        var candidateType = uniqueCandidateType(resolvedCall.candidates());
        if (StringUtils.hasText(candidateType)) {
            return candidateType;
        }
        if (StringUtils.hasText(resolvedCall.call().receiver())) {
            return normalizeTerminalReceiver(resolvedCall.call().receiver());
        }
        return resolvedCall.call().constructorCall() ? resolvedCall.call().name() : null;
    }

    private String uniqueCandidateType(List<String> candidates) {
        var candidateTypes = candidates.stream()
                .map(this::typeNameFromMethodId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return candidateTypes.size() == 1 ? candidateTypes.get(0) : null;
    }

    private String typeNameFromMethodId(String methodId) {
        if (!StringUtils.hasText(methodId)) {
            return null;
        }
        var separator = methodId.indexOf('#');
        return separator > 0 ? methodId.substring(0, separator) : null;
    }

    private String normalizeTerminalReceiver(String receiver) {
        if (!StringUtils.hasText(receiver)) {
            return receiver;
        }
        var trimmed = receiver.trim();
        if (trimmed.endsWith(".INSTANCE")) {
            return trimmed.substring(0, trimmed.length() - ".INSTANCE".length());
        }
        return trimmed;
    }

    private GitLabEndpointUseCaseRole roleForType(
            GitLabEndpointUseCaseTypeInfo type,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry
    ) {
        var bean = beanForType(type.fqn(), beanRegistry);
        if (bean != null) {
            if (bean.sourceKind() == GitLabEndpointUseCaseSpringBeanSourceKind.SPRING_DATA_REPOSITORY) {
                return GitLabEndpointUseCaseRole.REPOSITORY;
            }
            if (bean.sourceKind() == GitLabEndpointUseCaseSpringBeanSourceKind.FEIGN_CLIENT) {
                return GitLabEndpointUseCaseRole.EXTERNAL_CLIENT;
            }
            if (bean.sourceKind() == GitLabEndpointUseCaseSpringBeanSourceKind.MAPSTRUCT_MAPPER) {
                return GitLabEndpointUseCaseRole.MAPPER;
            }
            if (bean.sourceKind() == GitLabEndpointUseCaseSpringBeanSourceKind.CONFIGURATION_CLASS) {
                return GitLabEndpointUseCaseRole.CONFIGURATION;
            }
        }

        if (hasAnnotation(type, "RestController") || hasAnnotation(type, "Controller")) {
            return GitLabEndpointUseCaseRole.CONTROLLER;
        }
        if (hasAnnotation(type, "Configuration")) {
            return GitLabEndpointUseCaseRole.CONFIGURATION;
        }
        if (hasAnnotation(type, "Repository") || isRepositoryType(type)) {
            return GitLabEndpointUseCaseRole.REPOSITORY;
        }
        if (hasAnnotation(type, "FeignClient")) {
            return GitLabEndpointUseCaseRole.EXTERNAL_CLIENT;
        }
        if (hasAnnotation(type, "Mapper")) {
            return GitLabEndpointUseCaseRole.MAPPER;
        }

        var simpleName = type.simpleName();
        if (containsIgnoreCase(simpleName, "UseCase") || containsIgnoreCase(simpleName, "Interactor")) {
            return GitLabEndpointUseCaseRole.USE_CASE_SERVICE;
        }
        if (containsIgnoreCase(simpleName, "Policy")) {
            return GitLabEndpointUseCaseRole.POLICY;
        }
        if (containsIgnoreCase(simpleName, "Strategy")) {
            return GitLabEndpointUseCaseRole.STRATEGY;
        }
        if (containsIgnoreCase(simpleName, "Validator")) {
            return GitLabEndpointUseCaseRole.VALIDATOR;
        }
        if (containsIgnoreCase(simpleName, "Mapper")) {
            return GitLabEndpointUseCaseRole.MAPPER;
        }
        if (containsIgnoreCase(simpleName, "Repository")) {
            return GitLabEndpointUseCaseRole.REPOSITORY;
        }
        if (containsIgnoreCase(simpleName, "Client") || containsIgnoreCase(simpleName, "Gateway")) {
            return GitLabEndpointUseCaseRole.EXTERNAL_CLIENT;
        }
        if (containsIgnoreCase(simpleName, "EventPublisher") || containsIgnoreCase(simpleName, "Publisher")) {
            return GitLabEndpointUseCaseRole.EVENT_PUBLISHER;
        }
        if (hasAnnotation(type, "Service")) {
            return GitLabEndpointUseCaseRole.SERVICE;
        }
        if (!isLikelyDtoOrInfrastructure(type.simpleName())
                && (type.kind() == GitLabEndpointUseCaseTypeKind.CLASS || type.kind() == GitLabEndpointUseCaseTypeKind.RECORD)) {
            return GitLabEndpointUseCaseRole.DOMAIN_MODEL;
        }
        return GitLabEndpointUseCaseRole.UNKNOWN;
    }

    private GitLabEndpointUseCaseSpringBean beanForType(
            String type,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry
    ) {
        if (beanRegistry == null || !StringUtils.hasText(type)) {
            return null;
        }
        return beanRegistry.beans().stream()
                .filter(bean -> type.equals(bean.type()))
                .findFirst()
                .orElse(null);
    }

    private boolean hasAnnotation(GitLabEndpointUseCaseTypeInfo type, String annotationName) {
        return type.annotationDetails().stream()
                .anyMatch(annotation -> annotationName.equals(simpleName(annotation.name())));
    }

    private boolean isRepositoryType(GitLabEndpointUseCaseTypeInfo type) {
        return type.kind() == GitLabEndpointUseCaseTypeKind.INTERFACE
                && type.directParentTypes().stream()
                .map(this::simpleName)
                .anyMatch(parent -> parent.endsWith("Repository"));
    }

    private boolean isLikelyDtoOrInfrastructure(String simpleName) {
        var name = lower(simpleName);
        return name.endsWith("dto")
                || name.endsWith("request")
                || name.endsWith("response")
                || name.endsWith("command")
                || name.endsWith("event")
                || name.endsWith("exception")
                || name.endsWith("properties");
    }

    private boolean isEventPublishCall(GitLabEndpointUseCaseMethodCallInfo call) {
        if (call == null) {
            return false;
        }
        return "publishEvent".equals(call.name())
                || containsIgnoreCase(call.receiver(), "eventPublisher")
                || containsIgnoreCase(call.expression(), "ApplicationEventPublisher");
    }

    private void addDepthWarning(
            TraversalItem item,
            GraphContext context,
            List<GitLabEndpointUseCaseWarning> warnings,
            Set<String> depthWarnings
    ) {
        if (!depthWarnings.add(item.methodId())) {
            return;
        }
        var type = context.typeByMethodId().get(item.methodId());
        var method = context.methodById().get(item.methodId());
        warnings.add(new GitLabEndpointUseCaseWarning(
                GitLabEndpointUseCaseWarningCodes.MAX_DEPTH_REACHED,
                GitLabEndpointUseCaseWarningSeverity.INFO,
                "Call graph traversal stopped at maxDepth for method " + item.methodId() + ".",
                type != null ? type.sourcePath() : null,
                method != null ? method.lineStart() : null,
                List.of(item.methodId())
        ));
    }

    private void addMaxNodesWarning(
            GitLabEndpointUseCaseMethodCallInfo call,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        var alreadyAdded = warnings.stream()
                .anyMatch(warning -> GitLabEndpointUseCaseWarningCodes.MAX_NODES_REACHED.equals(warning.code()));
        if (alreadyAdded) {
            return;
        }
        warnings.add(new GitLabEndpointUseCaseWarning(
                GitLabEndpointUseCaseWarningCodes.MAX_NODES_REACHED,
                GitLabEndpointUseCaseWarningSeverity.WARNING,
                "Call graph traversal stopped because maxNodes was reached.",
                call != null ? call.sourcePath() : null,
                call != null ? call.line() : null,
                List.of()
        ));
    }

    private void addCycleWarning(
            TraversalItem item,
            GitLabEndpointUseCaseResolvedCall resolvedCall,
            List<GitLabEndpointUseCaseWarning> warnings,
            Set<String> cycleWarnings
    ) {
        var cycleKey = item.methodId() + "->" + resolvedCall.targetMethodId();
        if (!cycleWarnings.contains(cycleKey)) {
            return;
        }
        warnings.add(new GitLabEndpointUseCaseWarning(
                GitLabEndpointUseCaseWarningCodes.CALL_GRAPH_CYCLE_DETECTED,
                GitLabEndpointUseCaseWarningSeverity.INFO,
                "Call graph cycle detected and not expanded: " + cycleKey + ".",
                resolvedCall.call().sourcePath(),
                resolvedCall.call().line(),
                List.of(item.methodId(), resolvedCall.targetMethodId())
        ));
    }

    private List<String> appendPath(List<String> path, String methodId) {
        var copy = new ArrayList<>(path);
        copy.add(methodId);
        return List.copyOf(copy);
    }

    private String terminalNodeId(String callerMethodId, GitLabEndpointUseCaseMethodCallInfo call) {
        return "terminal:" + callerMethodId + ":" + Integer.toHexString(Objects.hash(
                call.line(),
                call.expression(),
                call.receiver(),
                call.name(),
                call.argumentCount()
        ));
    }

    private int line(Integer preferred, Integer fallback) {
        if (preferred != null) {
            return preferred;
        }
        return fallback != null ? fallback : -1;
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        return StringUtils.hasText(value)
                && StringUtils.hasText(fragment)
                && lower(value).contains(lower(fragment));
    }

    private String lower(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }

    private String simpleName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var normalized = value.trim();
        var genericIndex = normalized.indexOf('<');
        if (genericIndex >= 0) {
            normalized = normalized.substring(0, genericIndex);
        }
        var dotIndex = normalized.lastIndexOf('.');
        return dotIndex >= 0 ? normalized.substring(dotIndex + 1) : normalized;
    }

    private record AppendCallResult(boolean maxNodesReached, boolean cycleDetected) {
    }

    private record TraversalItem(String methodId, int depth, List<String> path) {
        private TraversalItem {
            path = path != null ? List.copyOf(path) : List.of();
        }
    }

    private record GraphContext(
            GitLabEndpointUseCaseCodeIndex codeIndex,
            GitLabEndpointUseCaseSpringBeanRegistry beanRegistry,
            GitLabEndpointUseCaseCallTargetResolution callTargetResolution,
            Map<String, GitLabEndpointUseCaseTypeInfo> typeByMethodId,
            Map<String, GitLabEndpointUseCaseMethodInfo> methodById,
            Map<String, List<GitLabEndpointUseCaseResolvedCall>> callsByCaller
    ) {
        private GraphContext(
                GitLabEndpointUseCaseCodeIndex codeIndex,
                GitLabEndpointUseCaseSpringBeanRegistry beanRegistry,
                GitLabEndpointUseCaseCallTargetResolution callTargetResolution
        ) {
            this(
                    codeIndex,
                    beanRegistry,
                    callTargetResolution,
                    typeByMethodId(codeIndex),
                    methodById(codeIndex),
                    callTargetResolution != null ? callTargetResolution.callsByCaller() : Map.of()
            );
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
