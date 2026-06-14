package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
class GitLabEndpointUseCaseContextCompressorService {

    private static final int COMPACT_EDGE_LIMIT = 40;
    private static final int BUSINESS_EDGE_LIMIT = 30;
    private static final int COMPACT_EVIDENCE_LIMIT = 30;
    private static final int BUSINESS_EVIDENCE_LIMIT = 20;
    private static final int SUGGESTED_READ_LIMIT = 12;

    GitLabEndpointUseCaseCompressedContext compress(
            GitLabEndpointUseCaseEndpointCandidate endpoint,
            GitLabEndpointUseCaseGraphBuildResult graphBuildResult,
            GitLabEndpointUseCaseOutputMode outputMode
    ) {
        var mode = outputMode != null ? outputMode : GitLabEndpointUseCaseOutputMode.COMPACT;
        var fullGraph = graphBuildResult != null ? graphBuildResult.graph() : GitLabEndpointUseCaseGraph.empty();
        var warnings = graphBuildResult != null ? graphBuildResult.warnings() : List.<GitLabEndpointUseCaseWarning>of();
        var limits = graphBuildResult != null ? graphBuildResult.limits() : GitLabEndpointUseCaseLimits.defaults();
        var graph = graphForMode(fullGraph, mode);
        var classList = classListForMode(fullGraph, mode);

        return new GitLabEndpointUseCaseCompressedContext(
                summary(endpoint, fullGraph),
                graph,
                classList,
                evidence(endpoint, fullGraph, mode),
                suggestedNextReads(fullGraph, mode),
                confidence(fullGraph, warnings, limits)
        );
    }

    private GitLabEndpointUseCaseGraph graphForMode(
            GitLabEndpointUseCaseGraph graph,
            GitLabEndpointUseCaseOutputMode mode
    ) {
        if (mode == GitLabEndpointUseCaseOutputMode.GRAPH || mode == GitLabEndpointUseCaseOutputMode.DEBUG) {
            return graph;
        }

        var nodeById = nodeById(graph);
        var selectedEdges = graph.edges().stream()
                .filter(edge -> mode == GitLabEndpointUseCaseOutputMode.BUSINESS
                        ? businessEdge(edge, nodeById)
                        : compactEdge(edge, nodeById))
                .limit(mode == GitLabEndpointUseCaseOutputMode.BUSINESS ? BUSINESS_EDGE_LIMIT : COMPACT_EDGE_LIMIT)
                .toList();

        var selectedNodeIds = new LinkedHashSet<String>();
        rootNode(graph).ifPresent(node -> selectedNodeIds.add(node.id()));
        for (var edge : selectedEdges) {
            selectedNodeIds.add(edge.from());
            selectedNodeIds.add(edge.to());
        }

        var selectedNodes = graph.nodes().stream()
                .filter(node -> selectedNodeIds.contains(node.id()))
                .filter(node -> mode != GitLabEndpointUseCaseOutputMode.BUSINESS || businessNode(node))
                .toList();

        return new GitLabEndpointUseCaseGraph(selectedNodes, selectedEdges);
    }

    private List<GitLabEndpointUseCaseClassItem> classListForMode(
            GitLabEndpointUseCaseGraph graph,
            GitLabEndpointUseCaseOutputMode mode
    ) {
        var byClass = new LinkedHashMap<String, ClassAccumulator>();
        for (var node : graph.nodes()) {
            if (!StringUtils.hasText(node.classFqn())) {
                continue;
            }
            if (mode == GitLabEndpointUseCaseOutputMode.BUSINESS && !businessNode(node)) {
                continue;
            }
            if (mode == GitLabEndpointUseCaseOutputMode.COMPACT && lowValueCompactNode(node)) {
                continue;
            }
            byClass.computeIfAbsent(node.classFqn(), ignored -> new ClassAccumulator(node.classFqn()))
                    .add(node);
        }

        return byClass.values().stream()
                .map(accumulator -> accumulator.toItem(methodLimit(mode)))
                .sorted(Comparator.comparingInt(GitLabEndpointUseCaseClassItem::depth)
                        .thenComparing(GitLabEndpointUseCaseClassItem::classFqn))
                .toList();
    }

    private GitLabEndpointUseCaseSummary summary(
            GitLabEndpointUseCaseEndpointCandidate endpoint,
            GitLabEndpointUseCaseGraph graph
    ) {
        var businessObjects = businessObjects(graph);
        var sideEffects = sideEffects(graph);
        var externalSystems = externalSystems(graph);
        var asyncBoundaries = asyncBoundaries(graph);
        return new GitLabEndpointUseCaseSummary(
                mainResponsibility(endpoint, graph, businessObjects),
                businessObjects,
                sideEffects,
                externalSystems,
                asyncBoundaries
        );
    }

    private String mainResponsibility(
            GitLabEndpointUseCaseEndpointCandidate endpoint,
            GitLabEndpointUseCaseGraph graph,
            List<String> businessObjects
    ) {
        var candidate = graph.nodes().stream()
                .filter(node -> node.role() == GitLabEndpointUseCaseRole.USE_CASE_SERVICE)
                .findFirst()
                .or(() -> graph.nodes().stream().filter(node -> node.role() == GitLabEndpointUseCaseRole.SERVICE).findFirst())
                .or(() -> graph.nodes().stream().filter(node -> node.role() == GitLabEndpointUseCaseRole.CONTROLLER).findFirst())
                .orElse(null);
        var methodName = methodName(candidate != null ? candidate.methodSignature()
                : endpoint != null ? endpoint.controllerMethod() : null);
        var action = humanizeIdentifier(methodName);
        if (!StringUtils.hasText(action)) {
            return null;
        }
        if (!businessObjects.isEmpty() && !containsIgnoreCase(action, businessObjects.get(0))) {
            return action + " " + lowerFirst(humanizeIdentifier(businessObjects.get(0)));
        }
        return action;
    }

    private List<String> businessObjects(GitLabEndpointUseCaseGraph graph) {
        var objects = new LinkedHashSet<String>();
        for (var node : graph.nodes()) {
            if (node.role() == GitLabEndpointUseCaseRole.EXTERNAL_CLIENT
                    || node.role() == GitLabEndpointUseCaseRole.EVENT_PUBLISHER
                    || node.role() == GitLabEndpointUseCaseRole.CONFIGURATION
                    || !StringUtils.hasText(node.classFqn())) {
                continue;
            }
            var object = businessObjectFromClass(simpleName(node.classFqn()));
            if (StringUtils.hasText(object)) {
                objects.add(object);
            }
        }
        return objects.stream().limit(12).toList();
    }

    private List<String> sideEffects(GitLabEndpointUseCaseGraph graph) {
        var sideEffects = new LinkedHashSet<String>();
        for (var edge : graph.edges()) {
            switch (edge.kind()) {
                case REPOSITORY_READ -> sideEffects.add("repository-read");
                case REPOSITORY_WRITE -> sideEffects.add("repository-write");
                case EXTERNAL_CALL -> sideEffects.add("external-call");
                case EVENT_PUBLISH -> sideEffects.add("event-publish");
                case ASYNC_BOUNDARY -> sideEffects.add("async-boundary");
                default -> {
                }
            }
        }
        return List.copyOf(sideEffects);
    }

    private List<String> externalSystems(GitLabEndpointUseCaseGraph graph) {
        var systems = new LinkedHashSet<String>();
        for (var node : graph.nodes()) {
            if (node.role() == GitLabEndpointUseCaseRole.EXTERNAL_CLIENT && StringUtils.hasText(node.classFqn())) {
                var system = stripKnownSuffix(simpleName(node.classFqn()),
                        List.of("Client", "Gateway", "Adapter", "Port", "Connector", "Api"));
                if (StringUtils.hasText(system)) {
                    systems.add(system);
                }
            }
        }
        return systems.stream().limit(12).toList();
    }

    private List<String> asyncBoundaries(GitLabEndpointUseCaseGraph graph) {
        var boundaries = new LinkedHashSet<String>();
        for (var edge : graph.edges()) {
            if (edge.kind() == GitLabEndpointUseCaseEdgeKind.EVENT_PUBLISH
                    || edge.kind() == GitLabEndpointUseCaseEdgeKind.ASYNC_BOUNDARY) {
                boundaries.add(edge.call());
            }
        }
        for (var node : graph.nodes()) {
            if (node.role() == GitLabEndpointUseCaseRole.EVENT_PUBLISHER && StringUtils.hasText(node.methodSignature())) {
                boundaries.add(node.methodSignature());
            }
        }
        return boundaries.stream().filter(StringUtils::hasText).limit(12).toList();
    }

    private List<GitLabEndpointUseCaseEvidence> evidence(
            GitLabEndpointUseCaseEndpointCandidate endpoint,
            GitLabEndpointUseCaseGraph graph,
            GitLabEndpointUseCaseOutputMode mode
    ) {
        var evidence = new ArrayList<GitLabEndpointUseCaseEvidence>();
        if (endpoint != null) {
            evidence.add(new GitLabEndpointUseCaseEvidence(
                    "endpoint",
                    "Matched endpoint " + endpoint.endpointId() + ".",
                    endpoint.sourcePath(),
                    endpoint.lineStart()
            ));
        }

        for (var node : graph.nodes()) {
            if (mode != GitLabEndpointUseCaseOutputMode.DEBUG && lowValueEvidenceNode(node)) {
                continue;
            }
            evidence.add(new GitLabEndpointUseCaseEvidence(
                    "node",
                    node.role() + " " + node.classFqn() + "#" + node.methodSignature()
                            + (node.terminal() ? " terminal: " + node.terminalReason() : ""),
                    node.sourcePath(),
                    normalizeLine(node.lineStart())
            ));
        }

        for (var edge : graph.edges()) {
            if (mode == GitLabEndpointUseCaseOutputMode.COMPACT && !compactEvidenceEdge(edge)) {
                continue;
            }
            if (mode == GitLabEndpointUseCaseOutputMode.BUSINESS && !businessEvidenceEdge(edge)) {
                continue;
            }
            evidence.add(new GitLabEndpointUseCaseEvidence(
                    "edge",
                    edge.kind() + " via " + edge.call() + " (" + edge.resolutionKind() + ").",
                    null,
                    edge.line()
            ));
        }

        return evidence.stream()
                .limit(mode == GitLabEndpointUseCaseOutputMode.BUSINESS ? BUSINESS_EVIDENCE_LIMIT : COMPACT_EVIDENCE_LIMIT)
                .toList();
    }

    private List<String> suggestedNextReads(
            GitLabEndpointUseCaseGraph graph,
            GitLabEndpointUseCaseOutputMode mode
    ) {
        var reads = new LinkedHashSet<String>();
        for (var node : graph.nodes()) {
            if (!StringUtils.hasText(node.sourcePath())) {
                continue;
            }
            if (mode == GitLabEndpointUseCaseOutputMode.COMPACT && lowValueCompactNode(node)) {
                continue;
            }
            if (mode == GitLabEndpointUseCaseOutputMode.BUSINESS && !businessNode(node)) {
                continue;
            }
            reads.add(node.sourcePath() + (node.lineStart() > 0 ? ":" + node.lineStart() : ""));
            if (reads.size() >= SUGGESTED_READ_LIMIT) {
                break;
            }
        }
        return List.copyOf(reads);
    }

    private GitLabEndpointUseCaseConfidence confidence(
            GitLabEndpointUseCaseGraph graph,
            List<GitLabEndpointUseCaseWarning> warnings,
            GitLabEndpointUseCaseLimits limits
    ) {
        if (graph.nodes().isEmpty()
                || limits.maxNodesReached()
                || hasWarning(warnings, GitLabEndpointUseCaseWarningCodes.ENDPOINT_NOT_FOUND)
                || hasWarning(warnings, GitLabEndpointUseCaseWarningCodes.ENDPOINT_AMBIGUOUS)) {
            return GitLabEndpointUseCaseConfidence.LOW;
        }
        if (limits.maxDepthReached()
                || hasWarning(warnings, GitLabEndpointUseCaseWarningCodes.CALL_TARGET_UNRESOLVED)
                || hasWarning(warnings, GitLabEndpointUseCaseWarningCodes.CALL_TARGET_AMBIGUOUS)
                || hasWarning(warnings, GitLabEndpointUseCaseWarningCodes.DI_BEAN_AMBIGUOUS)
                || hasWarning(warnings, GitLabEndpointUseCaseWarningCodes.SOURCE_PARSE_FAILED)) {
            return GitLabEndpointUseCaseConfidence.MEDIUM;
        }
        return GitLabEndpointUseCaseConfidence.HIGH;
    }

    private boolean compactEdge(
            GitLabEndpointUseCaseEdge edge,
            Map<String, GitLabEndpointUseCaseNode> nodeById
    ) {
        var target = nodeById.get(edge.to());
        var source = nodeById.get(edge.from());
        if (lombokGeneratedAccessorNode(target)) {
            return false;
        }
        return edge.kind() != GitLabEndpointUseCaseEdgeKind.SYNC_CALL
                || edge.ambiguous()
                || importantRole(target)
                || importantRole(source)
                || (target != null && target.terminal());
    }

    private boolean businessEdge(
            GitLabEndpointUseCaseEdge edge,
            Map<String, GitLabEndpointUseCaseNode> nodeById
    ) {
        var target = nodeById.get(edge.to());
        var source = nodeById.get(edge.from());
        return edge.kind() == GitLabEndpointUseCaseEdgeKind.REPOSITORY_READ
                || edge.kind() == GitLabEndpointUseCaseEdgeKind.REPOSITORY_WRITE
                || edge.kind() == GitLabEndpointUseCaseEdgeKind.EXTERNAL_CALL
                || edge.kind() == GitLabEndpointUseCaseEdgeKind.EVENT_PUBLISH
                || edge.kind() == GitLabEndpointUseCaseEdgeKind.ASYNC_BOUNDARY
                || edge.kind() == GitLabEndpointUseCaseEdgeKind.VALIDATION
                || businessNode(source) && businessNode(target);
    }

    private boolean compactEvidenceEdge(GitLabEndpointUseCaseEdge edge) {
        return edge.kind() != GitLabEndpointUseCaseEdgeKind.SYNC_CALL || edge.ambiguous();
    }

    private boolean businessEvidenceEdge(GitLabEndpointUseCaseEdge edge) {
        return edge.kind() == GitLabEndpointUseCaseEdgeKind.REPOSITORY_READ
                || edge.kind() == GitLabEndpointUseCaseEdgeKind.REPOSITORY_WRITE
                || edge.kind() == GitLabEndpointUseCaseEdgeKind.EXTERNAL_CALL
                || edge.kind() == GitLabEndpointUseCaseEdgeKind.EVENT_PUBLISH
                || edge.kind() == GitLabEndpointUseCaseEdgeKind.ASYNC_BOUNDARY
                || edge.kind() == GitLabEndpointUseCaseEdgeKind.VALIDATION;
    }

    private boolean businessNode(GitLabEndpointUseCaseNode node) {
        if (node == null) {
            return false;
        }
        return node.role() == GitLabEndpointUseCaseRole.CONTROLLER
                || node.role() == GitLabEndpointUseCaseRole.USE_CASE_SERVICE
                || node.role() == GitLabEndpointUseCaseRole.DOMAIN_SERVICE
                || node.role() == GitLabEndpointUseCaseRole.POLICY
                || node.role() == GitLabEndpointUseCaseRole.STRATEGY
                || node.role() == GitLabEndpointUseCaseRole.VALIDATOR
                || node.role() == GitLabEndpointUseCaseRole.REPOSITORY
                || node.role() == GitLabEndpointUseCaseRole.EXTERNAL_CLIENT
                || node.role() == GitLabEndpointUseCaseRole.EVENT_PUBLISHER
                || node.role() == GitLabEndpointUseCaseRole.DOMAIN_MODEL;
    }

    private boolean importantRole(GitLabEndpointUseCaseNode node) {
        if (node == null) {
            return false;
        }
        return node.role() == GitLabEndpointUseCaseRole.CONTROLLER
                || node.role() == GitLabEndpointUseCaseRole.USE_CASE_SERVICE
                || node.role() == GitLabEndpointUseCaseRole.SERVICE
                || node.role() == GitLabEndpointUseCaseRole.DOMAIN_SERVICE
                || node.role() == GitLabEndpointUseCaseRole.POLICY
                || node.role() == GitLabEndpointUseCaseRole.STRATEGY
                || node.role() == GitLabEndpointUseCaseRole.REPOSITORY
                || node.role() == GitLabEndpointUseCaseRole.EXTERNAL_CLIENT
                || node.role() == GitLabEndpointUseCaseRole.EVENT_PUBLISHER
                || node.role() == GitLabEndpointUseCaseRole.DOMAIN_MODEL;
    }

    private boolean lowValueCompactNode(GitLabEndpointUseCaseNode node) {
        return node.role() == GitLabEndpointUseCaseRole.CONFIGURATION
                || lombokGeneratedAccessorNode(node)
                || node.role() == GitLabEndpointUseCaseRole.UNKNOWN && !node.terminal();
    }

    private boolean lowValueEvidenceNode(GitLabEndpointUseCaseNode node) {
        return node.role() == GitLabEndpointUseCaseRole.CONFIGURATION
                || lombokGeneratedAccessorNode(node)
                || node.role() == GitLabEndpointUseCaseRole.UNKNOWN && !node.terminal();
    }

    private boolean lombokGeneratedAccessorNode(GitLabEndpointUseCaseNode node) {
        return node != null
                && node.terminal()
                && StringUtils.hasText(node.terminalReason())
                && node.terminalReason().startsWith("Lombok generated ");
    }

    private java.util.Optional<GitLabEndpointUseCaseNode> rootNode(GitLabEndpointUseCaseGraph graph) {
        return graph.nodes().stream()
                .filter(node -> node.depth() == 0)
                .findFirst();
    }

    private Map<String, GitLabEndpointUseCaseNode> nodeById(GitLabEndpointUseCaseGraph graph) {
        var nodes = new LinkedHashMap<String, GitLabEndpointUseCaseNode>();
        for (var node : graph.nodes()) {
            nodes.putIfAbsent(node.id(), node);
        }
        return Map.copyOf(nodes);
    }

    private int methodLimit(GitLabEndpointUseCaseOutputMode mode) {
        return switch (mode) {
            case DEBUG -> 20;
            case GRAPH -> 12;
            case BUSINESS -> 5;
            case COMPACT -> 8;
        };
    }

    private static String classReason(GitLabEndpointUseCaseRole role, boolean terminal, String terminalReason) {
        if (terminal && StringUtils.hasText(terminalReason)) {
            return terminalReason;
        }
        return switch (role) {
            case CONTROLLER -> "Endpoint handler.";
            case USE_CASE_SERVICE -> "Application use case reached from endpoint flow.";
            case SERVICE -> "Service participating in endpoint flow.";
            case DOMAIN_SERVICE -> "Domain service participating in endpoint flow.";
            case POLICY -> "Policy component used by endpoint flow.";
            case STRATEGY -> "Strategy component used by endpoint flow.";
            case VALIDATOR -> "Validation component used by endpoint flow.";
            case MAPPER -> "Mapper explicitly called in endpoint flow.";
            case REPOSITORY -> "Repository boundary reached from endpoint flow.";
            case EXTERNAL_CLIENT -> "External client boundary reached from endpoint flow.";
            case EVENT_PUBLISHER -> "Event publication boundary reached from endpoint flow.";
            case EVENT_LISTENER -> "Event listener related to endpoint flow.";
            case CONFIGURATION -> "Configuration class related to endpoint flow.";
            case DOMAIN_MODEL -> "Domain object with behavior reached from endpoint flow.";
            case UNKNOWN -> "Class reached from endpoint flow.";
        };
    }

    private String businessObjectFromClass(String simpleName) {
        var stripped = stripKnownSuffix(simpleName, List.of(
                "Controller",
                "UseCase",
                "Interactor",
                "Service",
                "Repository",
                "Client",
                "Gateway",
                "Mapper",
                "Validator",
                "Policy",
                "Strategy",
                "Adapter",
                "Port",
                "Handler",
                "Facade"
        ));
        stripped = stripLeadingAction(stripped);
        return StringUtils.hasText(stripped) && !"Default".equals(stripped) && !"Legacy".equals(stripped)
                ? stripped
                : null;
    }

    private String stripKnownSuffix(String value, List<String> suffixes) {
        var result = value;
        var changed = true;
        while (changed && StringUtils.hasText(result)) {
            changed = false;
            for (var suffix : suffixes) {
                if (result.endsWith(suffix) && result.length() > suffix.length()) {
                    result = result.substring(0, result.length() - suffix.length());
                    changed = true;
                    break;
                }
            }
        }
        return result;
    }

    private String stripLeadingAction(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        for (var action : List.of("Create", "Update", "Delete", "Remove", "Submit", "Approve", "Reject", "Cancel",
                "Get", "Find", "Search", "List", "Read", "Load", "Save", "Process", "Handle", "Validate")) {
            if (value.startsWith(action) && value.length() > action.length()) {
                return value.substring(action.length());
            }
        }
        return value;
    }

    private String humanizeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var cleaned = value.replace('_', ' ').replace('-', ' ').trim();
        var words = cleaned.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return StringUtils.hasText(words) ? words : null;
    }

    private String lowerFirst(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    private String methodName(String methodSignature) {
        if (!StringUtils.hasText(methodSignature)) {
            return null;
        }
        var name = methodSignature.trim();
        var parenthesisIndex = name.indexOf('(');
        return parenthesisIndex >= 0 ? name.substring(0, parenthesisIndex) : name;
    }

    private String simpleName(String fqn) {
        if (!StringUtils.hasText(fqn)) {
            return "";
        }
        var dotIndex = fqn.lastIndexOf('.');
        return dotIndex >= 0 ? fqn.substring(dotIndex + 1) : fqn;
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        return StringUtils.hasText(value)
                && StringUtils.hasText(fragment)
                && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private Integer normalizeLine(int line) {
        return line > 0 ? line : null;
    }

    private boolean hasWarning(List<GitLabEndpointUseCaseWarning> warnings, String code) {
        return warnings.stream().anyMatch(warning -> code.equals(warning.code()));
    }

    private static final class ClassAccumulator {
        private final String classFqn;
        private final LinkedHashSet<String> methods = new LinkedHashSet<>();
        private GitLabEndpointUseCaseRole role = GitLabEndpointUseCaseRole.UNKNOWN;
        private int depth = Integer.MAX_VALUE;
        private boolean terminal;
        private String terminalReason;

        private ClassAccumulator(String classFqn) {
            this.classFqn = classFqn;
        }

        private void add(GitLabEndpointUseCaseNode node) {
            if (StringUtils.hasText(node.methodSignature())) {
                methods.add(node.methodSignature());
            }
            role = mergeRole(role, node.role());
            depth = Math.min(depth, node.depth());
            terminal = terminal || node.terminal();
            if (!StringUtils.hasText(terminalReason) && StringUtils.hasText(node.terminalReason())) {
                terminalReason = node.terminalReason();
            }
        }

        private GitLabEndpointUseCaseClassItem toItem(int methodLimit) {
            var selectedMethods = methods.stream().limit(methodLimit).toList();
            return new GitLabEndpointUseCaseClassItem(
                    classFqn,
                    role,
                    depth == Integer.MAX_VALUE ? 0 : depth,
                    selectedMethods,
                    terminal,
                    classReason(role, terminal, terminalReason)
            );
        }

        private static GitLabEndpointUseCaseRole mergeRole(
                GitLabEndpointUseCaseRole current,
                GitLabEndpointUseCaseRole candidate
        ) {
            if (candidate == null || candidate == GitLabEndpointUseCaseRole.UNKNOWN) {
                return current;
            }
            if (current == null || current == GitLabEndpointUseCaseRole.UNKNOWN) {
                return candidate;
            }
            return priority(candidate) < priority(current) ? candidate : current;
        }

        private static int priority(GitLabEndpointUseCaseRole role) {
            return switch (role) {
                case CONTROLLER -> 0;
                case USE_CASE_SERVICE -> 1;
                case DOMAIN_SERVICE, POLICY, STRATEGY -> 2;
                case SERVICE -> 3;
                case VALIDATOR, MAPPER -> 4;
                case REPOSITORY, EXTERNAL_CLIENT, EVENT_PUBLISHER -> 5;
                case DOMAIN_MODEL -> 6;
                case EVENT_LISTENER, CONFIGURATION -> 7;
                case UNKNOWN -> 8;
            };
        }
    }
}
