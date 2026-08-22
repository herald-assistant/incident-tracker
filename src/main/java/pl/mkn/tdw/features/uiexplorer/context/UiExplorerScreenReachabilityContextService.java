package pl.mkn.tdw.features.uiexplorer.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerFrontendCatalogService;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerFrontendNotEligibleException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerSourceRefNotFoundException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerScreenSelectionStaleException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerScreenSourceUnavailableException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerReachabilityInputException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerSourceRevisionChangedException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerSourceRevisionUnavailableException;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerScreenIdentity;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionModeAssignment;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiagnosticSeverity;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendGraphLimits;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenSelectionRequest;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UiExplorerScreenReachabilityContextService {

    private static final int MAX_SYSTEM_ID_LENGTH = 120;
    private static final int MAX_REF_LENGTH = 160;
    private static final int MAX_SCREEN_ID_LENGTH = 240;
    private static final int MAX_REVISION_LENGTH = 160;
    private final UiExplorerFrontendCatalogService frontendCatalogService;
    private final GitLabFrontendScreenReachabilityService screenReachabilityService;

    public UiExplorerScreenReachabilityContext buildContext(
            String systemId,
            String ref,
            String screenId,
            String expectedRevision,
            List<UiExplorerSectionModeAssignment> sectionModes
    ) {
        var normalizedSystemId = required(systemId, "systemId", MAX_SYSTEM_ID_LENGTH);
        var normalizedRef = required(ref, "branch", MAX_REF_LENGTH);
        var normalizedScreenId = required(screenId, "screenId", MAX_SCREEN_ID_LENGTH);
        var normalizedRevision = required(expectedRevision, "sourceRevision", MAX_REVISION_LENGTH);
        var activeSections = activeSections(sectionModes);
        var frontend = frontendCatalogService.loadCatalog().findFrontend(normalizedSystemId)
                .orElseThrow(() -> new UiExplorerFrontendNotEligibleException(normalizedSystemId));
        var request = new GitLabFrontendScreenSelectionRequest(
                new GitLabFrontendRepositoryScope(
                        frontend.gitLabGroup(), frontend.gitLabProjectName(), normalizedRef, frontend.pathPrefixes()
                ),
                normalizedScreenId,
                normalizedRevision,
                GitLabFrontendGraphLimits.defaults()
        );
        try {
            return map(frontend.systemId(), frontend.label(), screenReachabilityService.build(request), activeSections);
        } catch (GitLabFrontendDiscoveryException exception) {
            throw mapDiscoveryFailure(exception, normalizedSystemId, normalizedRef, normalizedScreenId);
        }
    }

    private UiExplorerScreenReachabilityContext map(
            String systemId,
            String systemLabel,
            GitLabFrontendScreenReachabilityGraph graph,
            List<UiExplorerSectionModeAssignment> activeSections
    ) {
        var screenNode = graph.screenNode();
        var status = status(graph);
        var componentCount = graph.componentLevels().stream().mapToInt(level -> level.components().size()).sum();
        var coverage = activeSections.stream()
                .map(assignment -> new UiExplorerSectionContextCoverage(
                        assignment.sectionId(),
                        assignment.mode(),
                        sectionStatus(assignment.sectionId(), graph, status),
                        sourceCategories(assignment.sectionId()),
                        coverageDetail(assignment.sectionId(), graph, componentCount)
                ))
                .toList();
        var gaps = new LinkedHashSet<String>(graph.limitations());
        graph.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() != GitLabFrontendDiagnosticSeverity.INFO)
                .map(diagnostic -> diagnostic.message())
                .filter(StringUtils::hasText)
                .forEach(gaps::add);
        return new UiExplorerScreenReachabilityContext(
                systemId,
                systemLabel,
                new UiExplorerSourceScope(
                        graph.scope().group(), graph.scope().projectName(), graph.scope().ref(), graph.scope().pathPrefixes()
                ),
                new UiExplorerScreenIdentity(
                        systemId,
                        screenNode.screen().screenId(),
                        StringUtils.hasText(screenNode.label()) ? screenNode.label() : screenNode.routePattern(),
                        screenNode.routePattern(),
                        navigationContext(graph)
                ),
                screenNode.status().name(),
                screenNode.lazyBoundary(),
                guards(graph),
                graph.effectiveRouteChain().routeParameters(),
                screenNode.limitations(),
                new UiExplorerSourceReference(
                        null,
                        screenNode.routeSource().path(),
                        screenNode.routeSource().symbol(),
                        screenNode.routeSource().startLine(),
                        screenNode.routeSource().endLine()
                ),
                new UiExplorerSourceRevision(graph.sourceRevision().ref(), graph.sourceRevision().commitId()),
                status,
                graph,
                coverage,
                new UiExplorerReachabilityBoundary(
                        graph.effectiveRouteChain().segments().size(),
                        componentCount,
                        graph.dependencies().size(),
                        graph.edges().size(),
                        graph.sourceFileCount(),
                        graph.sourceCharacters(),
                        graph.sliceCharacters(),
                        graph.outlineCharacters(),
                        graph.contextLimitReached()
                ),
                List.copyOf(gaps),
                List.of()
        );
    }

    private UiExplorerCoverageStatus status(GitLabFrontendScreenReachabilityGraph graph) {
        if ("BLOCKED".equals(graph.status()) || graph.componentLevels().stream().allMatch(level -> level.components().isEmpty())) {
            return UiExplorerCoverageStatus.BLOCKED;
        }
        return "OK".equals(graph.status()) ? UiExplorerCoverageStatus.READY : UiExplorerCoverageStatus.PARTIAL;
    }

    private UiExplorerCoverageStatus sectionStatus(
            UiExplorerSectionId sectionId,
            GitLabFrontendScreenReachabilityGraph graph,
            UiExplorerCoverageStatus globalStatus
    ) {
        if (globalStatus == UiExplorerCoverageStatus.BLOCKED) {
            return UiExplorerCoverageStatus.BLOCKED;
        }
        if (graph.contextLimitReached()) {
            return UiExplorerCoverageStatus.PARTIAL;
        }
        var components = graph.componentLevels().stream().flatMap(level -> level.components().stream()).toList();
        var componentSlicesComplete = components.stream().noneMatch(component ->
                "PARTIAL".equals(component.status()) || "UNRESOLVED".equals(component.status()));
        var templatesComplete = components.stream().noneMatch(component ->
                StringUtils.hasText(component.templatePath()) && !StringUtils.hasText(component.templateContent()));
        var routedViewsComplete = graph.limitations().stream().noneMatch(limitation ->
                limitation.contains("routed child view") || limitation.contains("router-outlet"));
        var dependenciesComplete = graph.dependencies().stream().noneMatch(dependency ->
                "PARTIAL".equals(dependency.status()) || "UNRESOLVED".equals(dependency.status()));
        var complete = switch (sectionId) {
            case NAVIGATION_AND_ACCESS -> routedViewsComplete;
            case OVERVIEW, SCREEN_STRUCTURE -> componentSlicesComplete && templatesComplete && routedViewsComplete;
            case ACTIONS_AND_OUTCOMES, FORMS_AND_RULES, VARIANTS_AND_FAILURES ->
                    componentSlicesComplete && templatesComplete && routedViewsComplete;
            case DATA_AND_SERVICES, STATE_AND_SYNCHRONIZATION ->
                    componentSlicesComplete && routedViewsComplete && dependenciesComplete;
        };
        return complete ? UiExplorerCoverageStatus.READY : UiExplorerCoverageStatus.PARTIAL;
    }

    private List<String> sourceCategories(UiExplorerSectionId sectionId) {
        return switch (sectionId) {
            case OVERVIEW -> List.of("ROUTE_CHAIN", "ROUTED_VIEW_SUBTREE", "COMPONENT_BFS");
            case NAVIGATION_AND_ACCESS -> List.of("ROUTE_CHAIN", "ROUTED_VIEW_SUBTREE", "ROUTE_CONFIGURATION");
            case SCREEN_STRUCTURE -> List.of("ROUTED_VIEW_SUBTREE", "COMPONENT_BFS", "COMPONENT_TEMPLATES");
            case ACTIONS_AND_OUTCOMES -> List.of("COMPONENT_TEMPLATES", "COMPONENT_ENTRY_POINTS", "FUNCTIONAL_DEPENDENCIES");
            case FORMS_AND_RULES -> List.of("COMPONENT_TEMPLATES", "COMPONENT_ENTRY_POINTS", "FORM_DEPENDENCIES");
            case DATA_AND_SERVICES -> List.of("FUNCTIONAL_DEPENDENCIES", "BACKEND_OPERATIONS");
            case STATE_AND_SYNCHRONIZATION -> List.of("COMPONENT_ENTRY_POINTS", "STATE_DEPENDENCIES");
            case VARIANTS_AND_FAILURES -> List.of("COMPONENT_TEMPLATES", "COMPONENT_ENTRY_POINTS", "FUNCTIONAL_DEPENDENCIES");
        };
    }

    private String coverageDetail(
            UiExplorerSectionId sectionId,
            GitLabFrontendScreenReachabilityGraph graph,
            int componentCount
    ) {
        var templateCount = graph.componentLevels().stream()
                .flatMap(level -> level.components().stream())
                .filter(component -> StringUtils.hasText(component.templateContent()))
                .count();
        return switch (sectionId) {
            case NAVIGATION_AND_ACCESS -> graph.effectiveRouteChain().segments().size()
                    + " effective route segment(s) and the selected routed subtree were prepared.";
            case SCREEN_STRUCTURE, ACTIONS_AND_OUTCOMES, FORMS_AND_RULES, VARIANTS_AND_FAILURES ->
                    componentCount + " reachable component(s) and " + templateCount
                            + " rendered template(s) were prepared in breadth-first order.";
            case DATA_AND_SERVICES, STATE_AND_SYNCHRONIZATION -> componentCount
                    + " reachable component(s) and " + graph.dependencies().size()
                    + " deduplicated dependency slice(s) were prepared.";
            case OVERVIEW -> graph.effectiveRouteChain().segments().size() + " route segment(s), "
                    + componentCount + " reachable component(s) and the routed view subtree were prepared.";
        };
    }

    private RuntimeException mapDiscoveryFailure(
            GitLabFrontendDiscoveryException exception,
            String systemId,
            String ref,
            String screenId
    ) {
        return switch (exception.code()) {
            case "FRONTEND_REF_NOT_FOUND" -> new UiExplorerSourceRefNotFoundException(systemId, ref);
            case "FRONTEND_SOURCE_REVISION_CHANGED" -> new UiExplorerSourceRevisionChangedException(systemId, ref);
            case "FRONTEND_SOURCE_REVISION_UNRESOLVED" -> new UiExplorerSourceRevisionUnavailableException(systemId, ref);
            case "FRONTEND_SCREEN_NOT_FOUND" -> new UiExplorerScreenSelectionStaleException(systemId, screenId);
            default -> new UiExplorerScreenSourceUnavailableException(systemId, screenId);
        };
    }

    private String navigationContext(GitLabFrontendScreenReachabilityGraph graph) {
        var segments = graph.effectiveRouteChain().segments();
        return segments.size() > 1 ? segments.get(segments.size() - 2).routePattern() : "/";
    }

    private List<String> guards(GitLabFrontendScreenReachabilityGraph graph) {
        return graph.effectiveRouteChain().segments().stream()
                .flatMap(segment -> segment.configuration().stream())
                .filter(configuration -> configuration.kind().name().startsWith("CAN_"))
                .flatMap(configuration -> configuration.referencedSymbols().stream())
                .distinct()
                .toList();
    }

    private List<UiExplorerSectionModeAssignment> activeSections(
            List<UiExplorerSectionModeAssignment> sectionModes
    ) {
        var active = sectionModes != null
                ? sectionModes.stream()
                .filter(assignment -> assignment != null && assignment.sectionId() != null
                        && assignment.mode() != null && assignment.mode() != UiExplorerSectionMode.OFF)
                .toList()
                : List.<UiExplorerSectionModeAssignment>of();
        if (active.isEmpty()) {
            throw new UiExplorerReachabilityInputException("at least one section must be active");
        }
        return active;
    }

    private String required(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new UiExplorerReachabilityInputException(field + " must not be blank");
        }
        var normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new UiExplorerReachabilityInputException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
