package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GitLabFrontendScreenGraphContextService {

    private static final Pattern TEMPLATE_URL = Pattern.compile("templateUrl\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern STYLE_URL = Pattern.compile("(?:styleUrl|styleUrls)\\s*:\\s*(?:\\[\\s*)?['\"]([^'\"]+)['\"]");

    private final GitLabFrontendRouteGraphDiscoveryService graphDiscoveryService;
    private final GitLabRepositoryPort repositoryPort;
    private final AngularBootstrapSourceParser moduleParser = new AngularBootstrapSourceParser();

    public GitLabFrontendScreenGraphContext build(GitLabFrontendScreenGraphContextRequest request) {
        var graph = graphDiscoveryService.discover(request.scope(), request.limits());
        var screenNode = graph.nodes().stream()
                .filter(node -> node.screen() != null)
                .filter(node -> request.screenId().equals(node.screen().screenId()))
                .findFirst()
                .orElseThrow(() -> failure("FRONTEND_SCREEN_NOT_FOUND", "Selected screen is not present in the route graph."));
        var chain = graph.effectiveRouteChains().stream()
                .filter(candidate -> candidate.screen().equals(screenNode.screen()))
                .findFirst()
                .orElseThrow(() -> failure("FRONTEND_SCREEN_SOURCE_UNRESOLVED", "Selected screen route chain is unavailable."));
        verifyRevision(request.expectedRevision(), graph.sourceRevision());

        var session = new GitLabFrontendTargetedSourceSession(repositoryPort, request.scope(), request.limits());
        var imports = new GitLabFrontendTargetedImportResolver(session, graph.bootstrapRoot().bootstrapSource().path());
        var files = new LinkedHashMap<String, GitLabFrontendSourceFile>();
        for (var segment : chain.segments()) {
            addFile(segment.source().path(), GitLabFrontendSourceRole.ROUTE_CONFIGURATION, session, files);
        }
        var viewPath = screenNode.viewTarget() != null ? screenNode.viewTarget().sourcePath() : null;
        if (!StringUtils.hasText(viewPath)) {
            throw failure("FRONTEND_SCREEN_SOURCE_UNRESOLVED", "Selected screen component source is unavailable.");
        }
        var subtreeNodes = selectedSubtreeNodes(graph.nodes(), screenNode.nodeId());
        var descendantNodes = subtreeNodes.stream()
                .filter(node -> !node.nodeId().equals(screenNode.nodeId()))
                .toList();
        var viewRoots = new ArrayList<DependencyTask>();
        viewRoots.add(new DependencyTask(viewPath, 0, GitLabFrontendSourceRole.VIEW_COMPONENT));
        descendantNodes.stream()
                .filter(node -> node.viewTarget() != null)
                .map(node -> node.viewTarget().sourcePath())
                .filter(StringUtils::hasText)
                .distinct()
                .map(path -> new DependencyTask(path, 0, GitLabFrontendSourceRole.CHILD_COMPONENT))
                .forEach(viewRoots::add);

        // Read every routed view root before following general imports. This keeps
        // business-facing child views ahead of shared infrastructure when the
        // bounded context reaches a file or character limit.
        traverseDependencies(viewRoots, session, imports, files, request.limits());
        traverseRouteConfiguration(chain, session, imports, files, request.limits());
        traverseRouteConfiguration(descendantNodes, session, imports, files, request.limits());

        var signals = signals(files, chain);
        var coverage = coverage(files, signals, session.limitReached());
        var diagnostics = new ArrayList<>(graph.diagnostics());
        diagnostics.addAll(session.diagnostics());
        var totalCharacters = files.values().stream().mapToInt(GitLabFrontendSourceFile::returnedCharacters).sum();
        return new GitLabFrontendScreenGraphContext(
                request.scope(),
                graph.sourceRevision(),
                screenNode,
                chain,
                graph.coverage(),
                List.copyOf(files.values()),
                signals,
                coverage,
                diagnostics,
                totalCharacters,
                session.limitReached()
        );
    }

    private void verifyRevision(String expected, GitLabFrontendSourceRevision revision) {
        if (!StringUtils.hasText(expected)) {
            return;
        }
        if (!StringUtils.hasText(revision.commitId())) {
            throw failure("FRONTEND_SOURCE_REVISION_UNRESOLVED", "Exact source revision could not be confirmed.");
        }
        if (!expected.equals(revision.commitId())) {
            throw failure("FRONTEND_SOURCE_REVISION_CHANGED", "Source revision changed after screen selection.");
        }
    }

    private void traverseRouteConfiguration(
            GitLabFrontendEffectiveRouteChain chain,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            LinkedHashMap<String, GitLabFrontendSourceFile> files,
            GitLabFrontendGraphLimits limits
    ) {
        for (var segment : chain.segments()) {
            traverseRouteConfiguration(
                    segment.source().path(), segment.configuration(), session, imports, files, limits
            );
        }
    }

    private void traverseRouteConfiguration(
            List<GitLabFrontendRouteNode> nodes,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            LinkedHashMap<String, GitLabFrontendSourceFile> files,
            GitLabFrontendGraphLimits limits
    ) {
        for (var node : nodes) {
            addFile(node.routeSource().path(), GitLabFrontendSourceRole.ROUTE_CONFIGURATION, session, files);
            traverseRouteConfiguration(
                    node.routeSource().path(), node.configuration(), session, imports, files, limits
            );
        }
    }

    private void traverseRouteConfiguration(
            String sourcePath,
            List<GitLabFrontendRouteConfiguration> configuration,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            LinkedHashMap<String, GitLabFrontendSourceFile> files,
            GitLabFrontendGraphLimits limits
    ) {
        var routeSource = session.readOptional(sourcePath);
        if (routeSource == null) {
            return;
        }
        var parsed = moduleParser.parse(sourcePath, routeSource);
        var roots = new ArrayList<DependencyTask>();
        for (var item : configuration) {
            var role = item.kind().name().startsWith("CAN_")
                    ? GitLabFrontendSourceRole.AUTHORIZATION
                    : GitLabFrontendSourceRole.RELATED_SOURCE;
            for (var symbol : item.referencedSymbols()) {
                var binding = parsed.imports().get(symbol);
                if (binding == null) {
                    continue;
                }
                for (var target : imports.resolve(sourcePath, binding.moduleSpecifier())) {
                    roots.add(new DependencyTask(target, 0, role));
                }
            }
        }
        traverseDependencies(roots, session, imports, files, limits);
    }

    private void traverseDependencies(
            List<DependencyTask> roots,
            GitLabFrontendTargetedSourceSession session,
            GitLabFrontendTargetedImportResolver imports,
            LinkedHashMap<String, GitLabFrontendSourceFile> files,
            GitLabFrontendGraphLimits limits
    ) {
        var queue = new ArrayDeque<DependencyTask>();
        queue.addAll(roots);
        var visited = new LinkedHashSet<String>();
        while (!queue.isEmpty()) {
            var task = queue.removeFirst();
            var path = GitLabFrontendTargetedSourceSession.normalize(task.path());
            if (!visited.add(path)) {
                continue;
            }
            if (task.depth() > limits.maxComponentDepth()) {
                session.diagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        GitLabFrontendGraphDiagnosticCode.COMPONENT_DEPTH_LIMIT_REACHED,
                        "Selected component context reached maxComponentDepth=" + limits.maxComponentDepth() + ".",
                        path
                );
                continue;
            }
            var role = task.depth() == 0 ? task.rootRole() : role(path, null);
            var source = addFile(path, role, session, files);
            if (source == null || !path.endsWith(".ts")) {
                continue;
            }
            var parsed = moduleParser.parse(path, source);
            for (var binding : parsed.imports().values()) {
                for (var target : imports.resolve(path, binding.moduleSpecifier())) {
                    queue.add(new DependencyTask(target, task.depth() + 1, task.rootRole()));
                }
            }
            for (var reExport : parsed.reExports()) {
                for (var target : imports.resolve(path, reExport.moduleSpecifier())) {
                    queue.add(new DependencyTask(target, task.depth() + 1, task.rootRole()));
                }
            }
            enqueueResources(TEMPLATE_URL, path, source, GitLabFrontendSourceRole.TEMPLATE, task.depth(), queue);
            enqueueResources(STYLE_URL, path, source, GitLabFrontendSourceRole.STYLE, task.depth(), queue);
        }
    }

    private List<GitLabFrontendRouteNode> selectedSubtreeNodes(
            List<GitLabFrontendRouteNode> nodes,
            String selectedNodeId
    ) {
        var byId = nodes.stream().collect(java.util.stream.Collectors.toMap(
                GitLabFrontendRouteNode::nodeId,
                node -> node,
                (left, right) -> left,
                LinkedHashMap::new
        ));
        return nodes.stream()
                .filter(node -> node.nodeId().equals(selectedNodeId)
                        || hasAncestor(node, selectedNodeId, byId))
                .toList();
    }

    private boolean hasAncestor(
            GitLabFrontendRouteNode node,
            String selectedNodeId,
            Map<String, GitLabFrontendRouteNode> byId
    ) {
        var visited = new LinkedHashSet<String>();
        var parentId = node.parentNodeId();
        while (StringUtils.hasText(parentId) && visited.add(parentId)) {
            if (selectedNodeId.equals(parentId)) {
                return true;
            }
            var parent = byId.get(parentId);
            parentId = parent != null ? parent.parentNodeId() : null;
        }
        return false;
    }

    private void enqueueResources(
            Pattern pattern,
            String ownerPath,
            String source,
            GitLabFrontendSourceRole role,
            int ownerDepth,
            ArrayDeque<DependencyTask> queue
    ) {
        var matcher = pattern.matcher(source);
        while (matcher.find()) {
            queue.add(new DependencyTask(relative(ownerPath, matcher.group(1)), ownerDepth + 1, role));
        }
    }

    private String addFile(
            String path,
            GitLabFrontendSourceRole requestedRole,
            GitLabFrontendTargetedSourceSession session,
            LinkedHashMap<String, GitLabFrontendSourceFile> files
    ) {
        var normalized = GitLabFrontendTargetedSourceSession.normalize(path);
        if (files.containsKey(normalized)) {
            var current = files.get(normalized);
            var roles = new LinkedHashSet<>(current.roles());
            roles.add(requestedRole);
            files.put(normalized, new GitLabFrontendSourceFile(
                    current.path(), List.copyOf(roles), current.content(), current.returnedCharacters(), current.truncated()
            ));
            return current.content();
        }
        if (!session.markContextFile(normalized)) {
            return null;
        }
        var source = session.readRequired(normalized);
        if (source == null) {
            return null;
        }
        var roles = new LinkedHashSet<GitLabFrontendSourceRole>();
        roles.add(requestedRole);
        roles.add(role(normalized, source));
        files.put(normalized, new GitLabFrontendSourceFile(
                normalized, List.copyOf(roles), source, source.length(), false
        ));
        return source;
    }

    private GitLabFrontendSourceRole role(String path, String source) {
        var lower = path.toLowerCase(Locale.ROOT);
        var text = source != null ? source : "";
        if (lower.endsWith(".html")) return GitLabFrontendSourceRole.TEMPLATE;
        if (lower.endsWith(".scss") || lower.endsWith(".css")) return GitLabFrontendSourceRole.STYLE;
        if (lower.contains("guard") || text.contains("canActivate") || text.contains("Keycloak")) return GitLabFrontendSourceRole.AUTHORIZATION;
        if (lower.contains("effect") || lower.contains("reducer") || lower.contains("selector") || text.contains("@ngrx/")) return GitLabFrontendSourceRole.STATE_MANAGEMENT;
        if (lower.contains("client") || lower.contains("service") || text.contains("HttpClient")) return GitLabFrontendSourceRole.BACKEND_CLIENT;
        if (text.contains("WebSocket") || text.contains("webSocket(")) return GitLabFrontendSourceRole.WEBSOCKET_STREAM;
        if (text.contains("FormGroup") || text.contains("FormControl") || text.contains("ControlValueAccessor")) return GitLabFrontendSourceRole.FORM_LOGIC;
        if (lower.contains("component")) return GitLabFrontendSourceRole.CHILD_COMPONENT;
        return GitLabFrontendSourceRole.RELATED_SOURCE;
    }

    private List<GitLabFrontendTechnicalSignal> signals(
            LinkedHashMap<String, GitLabFrontendSourceFile> files,
            GitLabFrontendEffectiveRouteChain chain
    ) {
        var result = new ArrayList<GitLabFrontendTechnicalSignal>();
        for (var file : files.values()) {
            signal(result, file, "FormGroup|FormControl|formControlName", GitLabFrontendTechnicalSignalKind.REACTIVE_FORM, "Reactive form declarations are present.");
            signal(result, file, "ControlValueAccessor|NG_VALUE_ACCESSOR", GitLabFrontendTechnicalSignalKind.CUSTOM_FORM_CONTROL, "Custom form control contract is present.");
            signal(result, file, "@ngrx/store|Store<|store\\.", GitLabFrontendTechnicalSignalKind.NGRX_STORE, "NgRx store usage is present.");
            signal(result, file, "HttpClient", GitLabFrontendTechnicalSignalKind.HTTP_CLIENT, "Angular HTTP client usage is present.");
            signal(result, file, "Client|ApiService", GitLabFrontendTechnicalSignalKind.REST_CLIENT, "Generated or custom backend client usage is present.");
            signal(result, file, "WebSocket|webSocket\\(", GitLabFrontendTechnicalSignalKind.WEBSOCKET, "WebSocket usage is present.");
        }
        var guardConfiguration = chain.segments().stream()
                .flatMap(segment -> segment.configuration().stream())
                .filter(configuration -> configuration.kind().name().startsWith("CAN_"))
                .findFirst();
        guardConfiguration.ifPresent(configuration -> result.add(new GitLabFrontendTechnicalSignal(
                GitLabFrontendTechnicalSignalKind.AUTH_GUARD,
                "Route chain contains access guards: " + String.join(", ", configuration.referencedSymbols()) + ".",
                GitLabFrontendSignalConfidence.HIGH,
                configuration.source()
        )));
        return List.copyOf(result);
    }

    private void signal(
            List<GitLabFrontendTechnicalSignal> target,
            GitLabFrontendSourceFile file,
            String expression,
            GitLabFrontendTechnicalSignalKind kind,
            String description
    ) {
        if (Pattern.compile(expression).matcher(file.content()).find()) {
            target.add(new GitLabFrontendTechnicalSignal(
                    kind, description, GitLabFrontendSignalConfidence.HIGH,
                    new GitLabFrontendSourceReference(file.path(), null, null, null)
            ));
        }
    }

    private List<GitLabFrontendContextCoverage> coverage(
            LinkedHashMap<String, GitLabFrontendSourceFile> files,
            List<GitLabFrontendTechnicalSignal> signals,
            boolean limited
    ) {
        var roles = files.values().stream().flatMap(file -> file.roles().stream()).collect(java.util.stream.Collectors.toSet());
        return List.of(
                category("ROUTING", roles.contains(GitLabFrontendSourceRole.ROUTE_CONFIGURATION), limited),
                category("VIEW", roles.contains(GitLabFrontendSourceRole.VIEW_COMPONENT), limited),
                category("TEMPLATE", roles.contains(GitLabFrontendSourceRole.TEMPLATE), limited),
                category("FORMS", signals.stream().anyMatch(signal -> signal.kind() == GitLabFrontendTechnicalSignalKind.REACTIVE_FORM || signal.kind() == GitLabFrontendTechnicalSignalKind.CUSTOM_FORM_CONTROL), limited),
                category("STATE", roles.contains(GitLabFrontendSourceRole.STATE_MANAGEMENT), limited),
                category("BACKEND_SERVICES", roles.contains(GitLabFrontendSourceRole.BACKEND_CLIENT) || roles.contains(GitLabFrontendSourceRole.WEBSOCKET_STREAM), limited),
                category("AUTHORIZATION", signals.stream().anyMatch(signal -> signal.kind() == GitLabFrontendTechnicalSignalKind.AUTH_GUARD), limited)
        );
    }

    private GitLabFrontendContextCoverage category(String name, boolean present, boolean limited) {
        return new GitLabFrontendContextCoverage(
                name,
                present ? limited ? GitLabFrontendCoverageStatus.PARTIAL : GitLabFrontendCoverageStatus.READY : GitLabFrontendCoverageStatus.PARTIAL,
                present ? "Selected route/component graph contains this source category." : "No static signal for this category was found in the bounded selected-screen graph."
        );
    }

    private String relative(String ownerPath, String relative) {
        var separator = ownerPath.lastIndexOf('/');
        var parent = separator >= 0 ? ownerPath.substring(0, separator) : "";
        return GitLabFrontendTargetedSourceSession.normalize(parent + "/" + relative);
    }

    private GitLabFrontendDiscoveryException failure(String code, String message) {
        return new GitLabFrontendDiscoveryException(code, message);
    }

    private record DependencyTask(String path, int depth, GitLabFrontendSourceRole rootRole) {
    }
}
