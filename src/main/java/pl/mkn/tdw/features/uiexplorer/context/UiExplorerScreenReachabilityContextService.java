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
    private static final List<String> REACHABILITY_CATEGORIES = List.of(
            "ROUTE_CHAIN", "COMPONENT_BFS", "FUNCTIONAL_DEPENDENCIES"
    );

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
                        status,
                        REACHABILITY_CATEGORIES,
                        "Effective route chain, " + componentCount + " reachable components and "
                                + graph.dependencies().size() + " deduplicated dependencies were prepared."
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
