package pl.mkn.tdw.features.uiexplorer.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerFrontendCatalogService;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerFrontendNotEligibleException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerSourceRefNotFoundException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerScreenSelectionStaleException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerScreenSourceUnavailableException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerSourceContextInputException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerSourceRevisionChangedException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerSourceRevisionUnavailableException;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerScreenIdentity;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionModeAssignment;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendContextCoverage;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendCoverageStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiagnosticSeverity;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendGraphLimits;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenGraphContext;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenGraphContextRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenGraphContextService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UiExplorerSourceContextService {

    private static final int MAX_SYSTEM_ID_LENGTH = 120;
    private static final int MAX_REF_LENGTH = 160;
    private static final int MAX_SCREEN_ID_LENGTH = 240;
    private static final int MAX_REVISION_LENGTH = 160;

    private static final Map<UiExplorerSectionId, List<String>> SECTION_SOURCE_CATEGORIES = Map.of(
            UiExplorerSectionId.OVERVIEW, List.of("ROUTING", "VIEW"),
            UiExplorerSectionId.NAVIGATION_AND_ACCESS, List.of("ROUTING", "AUTHORIZATION"),
            UiExplorerSectionId.SCREEN_STRUCTURE, List.of("VIEW", "TEMPLATE"),
            UiExplorerSectionId.ACTIONS_AND_OUTCOMES, List.of("VIEW", "BACKEND_SERVICES"),
            UiExplorerSectionId.FORMS_AND_RULES, List.of("FORMS"),
            UiExplorerSectionId.DATA_AND_SERVICES, List.of("BACKEND_SERVICES"),
            UiExplorerSectionId.STATE_AND_SYNCHRONIZATION, List.of("STATE"),
            UiExplorerSectionId.VARIANTS_AND_FAILURES, List.of("VIEW", "BACKEND_SERVICES")
    );

    private final UiExplorerFrontendCatalogService frontendCatalogService;
    private final GitLabFrontendScreenGraphContextService screenGraphContextService;

    public UiExplorerSourceContextSnapshot buildContext(
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
        var limits = GitLabFrontendGraphLimits.defaults();
        var request = new GitLabFrontendScreenGraphContextRequest(
                new GitLabFrontendRepositoryScope(
                        frontend.gitLabGroup(),
                        frontend.gitLabProjectName(),
                        normalizedRef,
                        frontend.pathPrefixes()
                ),
                normalizedScreenId,
                normalizedRevision,
                limits
        );

        try {
            var source = screenGraphContextService.build(request);
            return map(frontend.systemId(), frontend.label(), source, activeSections, limits);
        } catch (GitLabFrontendDiscoveryException exception) {
            throw mapDiscoveryFailure(
                    exception,
                    normalizedSystemId,
                    normalizedRef,
                    normalizedScreenId
            );
        }
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
            case "FRONTEND_SOURCE_REVISION_UNRESOLVED" ->
                    new UiExplorerSourceRevisionUnavailableException(systemId, ref);
            case "FRONTEND_SCREEN_NOT_FOUND" -> new UiExplorerScreenSelectionStaleException(systemId, screenId);
            case "FRONTEND_SCREEN_SOURCE_UNRESOLVED" ->
                    new UiExplorerScreenSourceUnavailableException(systemId, screenId);
            default -> new UiExplorerScreenSourceUnavailableException(systemId, screenId);
        };
    }

    private UiExplorerSourceContextSnapshot map(
            String systemId,
            String systemLabel,
            GitLabFrontendScreenGraphContext source,
            List<UiExplorerSectionModeAssignment> activeSections,
            GitLabFrontendGraphLimits limits
    ) {
        var screen = source.screenNode();
        var manifest = source.sourceManifest().stream()
                .map(file -> new UiExplorerSourceManifestEntry(
                        file.path(), file.roles().stream().map(Enum::name).toList(),
                        file.sourceCharacters(), file.contentSha256(), file.sliceCount()
                ))
                .toList();
        var slices = source.sourceSlices().stream()
                .map(slice -> new UiExplorerSourceSlice(
                        slice.sliceId(), slice.path(), slice.roles().stream().map(Enum::name).toList(),
                        slice.kind().name(), slice.symbol(), slice.startLine(), slice.endLine(),
                        slice.content(), slice.returnedCharacters(), slice.contentSha256()
                ))
                .toList();
        var relations = source.relations().stream()
                .map(relation -> new UiExplorerUseCaseRelation(
                        relation.from(), relation.to(), relation.kind().name(), relation.symbol(),
                        relation.confidence().name(), relation.source() != null
                        ? new UiExplorerSourceReference(
                                null, relation.source().path(), relation.source().symbol(),
                                relation.source().startLine(), relation.source().endLine()
                        )
                        : null
                ))
                .toList();
        var unresolvedFrontier = source.unresolvedFrontier().stream()
                .map(frontier -> new UiExplorerUnresolvedFrontier(
                        frontier.frontierId(), frontier.ownerPath(), frontier.symbol(),
                        frontier.reason(), frontier.affectedCategories(), frontier.candidates()
                ))
                .toList();
        var sourceMetrics = source.metrics();
        var metrics = new UiExplorerContextMetrics(
                sourceMetrics.sourceFileCount(), sourceMetrics.sourceCharactersRead(),
                sourceMetrics.returnedSliceCount(), sourceMetrics.returnedCharacters(),
                sourceMetrics.omittedCharacters(), sourceMetrics.omittedFileCount(),
                sourceMetrics.relationCount(), sourceMetrics.unresolvedFrontierCount()
        );
        var signals = source.technicalSignals().stream()
                .map(signal -> new UiExplorerSourceContextSignal(
                        signal.kind().name(),
                        signal.description(),
                        signal.confidence().name(),
                        new UiExplorerSourceReference(
                                null,
                                signal.source().path(),
                                signal.source().symbol(),
                                signal.source().startLine(),
                                signal.source().endLine()
                        )
                ))
                .toList();
        var coverageByCategory = new LinkedHashMap<String, GitLabFrontendContextCoverage>();
        source.coverage().forEach(coverage -> coverageByCategory.put(coverage.category(), coverage));
        var sectionCoverage = activeSections.stream()
                .map(assignment -> mapCoverage(assignment, coverageByCategory, source))
                .toList();
        var diagnostics = source.diagnostics().stream()
                .map(diagnostic -> new UiExplorerSourceContextDiagnostic(
                        diagnostic.severity().name(),
                        diagnostic.code().name(),
                        diagnostic.message(),
                        diagnostic.source() != null ? diagnostic.source().path() : null
                ))
                .toList();
        var graphCoverage = source.graphCoverage();
        var boundary = new UiExplorerSourceContextBoundary(
                graphCoverage.visitedRouteNodeCount(),
                graphCoverage.visitedRouteFileCount(),
                graphCoverage.sourceReadCount(),
                graphCoverage.aliasResolutionCount(),
                graphCoverage.unresolvedEdgeCount(),
                manifest.size(),
                metrics.returnedCharacters(),
                graphCoverage.limitReached(),
                source.contextLimitReached(),
                limits.maxRouteNodes(),
                limits.maxRouteFiles(),
                limits.maxSourceReads(),
                limits.maxAliasResolutions(),
                limits.maxImportDepth(),
                limits.maxComponentDepth(),
                limits.maxContextFiles(),
                limits.maxFileCharacters(),
                limits.maxTotalCharacters()
        );
        return new UiExplorerSourceContextSnapshot(
                systemId,
                systemLabel,
                new UiExplorerSourceContextScope(
                        source.scope().group(),
                        source.scope().projectName(),
                        source.scope().ref(),
                        source.scope().pathPrefixes()
                ),
                new UiExplorerScreenIdentity(
                        systemId,
                        screen.screen().screenId(),
                        StringUtils.hasText(screen.label()) ? screen.label() : screen.routePattern(),
                        screen.routePattern(),
                        navigationContext(source)
                ),
                screen.status().name(),
                screen.lazyBoundary(),
                guards(source),
                source.effectiveRouteChain().routeParameters(),
                screen.limitations(),
                new UiExplorerSourceReference(
                        null,
                        screen.routeSource().path(),
                        screen.routeSource().symbol(),
                        screen.routeSource().startLine(),
                        screen.routeSource().endLine()
                ),
                new UiExplorerSourceRevision(
                        source.sourceRevision().ref(),
                        source.sourceRevision().commitId()
                ),
                overallStatus(slices, sectionCoverage, source),
                manifest,
                slices,
                relations,
                unresolvedFrontier,
                metrics,
                signals,
                sectionCoverage,
                diagnostics,
                boundary,
                visibilityLimits(source)
        );
    }

    private UiExplorerSectionContextCoverage mapCoverage(
            UiExplorerSectionModeAssignment assignment,
            Map<String, GitLabFrontendContextCoverage> coverageByCategory,
            GitLabFrontendScreenGraphContext source
    ) {
        var categories = SECTION_SOURCE_CATEGORIES.get(assignment.sectionId());
        var status = categories.stream()
                .map(category -> coverageByCategory.getOrDefault(
                        category,
                        new GitLabFrontendContextCoverage(
                                category,
                                GitLabFrontendCoverageStatus.BLOCKED,
                                "Source category was not produced by deterministic discovery."
                        )
                ))
                .map(GitLabFrontendContextCoverage::status)
                .map(this::mapStatus)
                .reduce(UiExplorerCoverageStatus.READY, this::mergeStatus);
        if (status == UiExplorerCoverageStatus.READY
                && (source.contextLimitReached()
                || source.graphCoverage().limitReached()
                || source.screenNode().status() != GitLabFrontendDiscoveryStatus.RESOLVED
                || source.unresolvedFrontier().stream().anyMatch(frontier ->
                        frontier.affectedCategories().isEmpty()
                                || frontier.affectedCategories().stream().anyMatch(categories::contains))
                || assignment.sectionId() == UiExplorerSectionId.VARIANTS_AND_FAILURES)) {
            status = UiExplorerCoverageStatus.PARTIAL;
        }
        var detail = categories.stream()
                .map(category -> {
                    var coverage = coverageByCategory.get(category);
                    return category + ": " + (coverage != null
                            ? coverage.detail()
                            : "Source category was not produced by deterministic discovery.");
                })
                .collect(java.util.stream.Collectors.joining(" "));
        return new UiExplorerSectionContextCoverage(
                assignment.sectionId(),
                assignment.mode(),
                status,
                categories,
                detail
        );
    }

    private UiExplorerCoverageStatus overallStatus(
            List<UiExplorerSourceSlice> slices,
            List<UiExplorerSectionContextCoverage> coverage,
            GitLabFrontendScreenGraphContext source
    ) {
        if (slices.isEmpty()) {
            return UiExplorerCoverageStatus.BLOCKED;
        }
        var status = coverage.stream()
                .map(UiExplorerSectionContextCoverage::status)
                .reduce(UiExplorerCoverageStatus.READY, this::mergeStatus);
        var materialDiagnostic = source.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.severity() != GitLabFrontendDiagnosticSeverity.INFO);
        if (status == UiExplorerCoverageStatus.READY
                && (source.contextLimitReached()
                || source.graphCoverage().limitReached()
                || source.screenNode().status() != GitLabFrontendDiscoveryStatus.RESOLVED
                || materialDiagnostic)) {
            return UiExplorerCoverageStatus.PARTIAL;
        }
        return status;
    }

    private List<String> visibilityLimits(GitLabFrontendScreenGraphContext source) {
        var limits = new ArrayList<String>();
        limits.add("Static discovery does not execute TypeScript or runtime form definitions.");
        limits.add("Organizational libraries outside the resolved repository scope may remain unavailable.");
        if (source.screenNode().status() != GitLabFrontendDiscoveryStatus.RESOLVED) {
            limits.add("The selected route-to-view mapping is not fully unambiguous.");
        }
        return List.copyOf(limits);
    }

    private String navigationContext(GitLabFrontendScreenGraphContext source) {
        var segments = source.effectiveRouteChain().segments();
        return segments.size() > 1 ? segments.get(segments.size() - 2).routePattern() : "/";
    }

    private List<String> guards(GitLabFrontendScreenGraphContext source) {
        return source.effectiveRouteChain().segments().stream()
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
                        .filter(assignment -> assignment != null
                                && assignment.sectionId() != null
                                && assignment.mode() != null
                                && assignment.mode() != UiExplorerSectionMode.OFF)
                        .toList()
                : List.<UiExplorerSectionModeAssignment>of();
        if (active.isEmpty()) {
            throw new UiExplorerSourceContextInputException("at least one section must be active");
        }
        return active;
    }

    private UiExplorerCoverageStatus mapStatus(GitLabFrontendCoverageStatus status) {
        return switch (status) {
            case READY -> UiExplorerCoverageStatus.READY;
            case PARTIAL -> UiExplorerCoverageStatus.PARTIAL;
            case BLOCKED -> UiExplorerCoverageStatus.BLOCKED;
        };
    }

    private UiExplorerCoverageStatus mergeStatus(
            UiExplorerCoverageStatus left,
            UiExplorerCoverageStatus right
    ) {
        if (left == UiExplorerCoverageStatus.BLOCKED || right == UiExplorerCoverageStatus.BLOCKED) {
            return UiExplorerCoverageStatus.BLOCKED;
        }
        if (left == UiExplorerCoverageStatus.PARTIAL || right == UiExplorerCoverageStatus.PARTIAL) {
            return UiExplorerCoverageStatus.PARTIAL;
        }
        return UiExplorerCoverageStatus.READY;
    }

    private String required(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new UiExplorerSourceContextInputException(field + " must not be blank");
        }
        var normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new UiExplorerSourceContextInputException(
                    field + " must not exceed " + maxLength + " characters"
            );
        }
        return normalized;
    }
}
