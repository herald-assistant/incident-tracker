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
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryLimits;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenContextRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenSourceContext;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceDiscoveryService;

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
    private final GitLabFrontendSourceDiscoveryService sourceDiscoveryService;

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
        var limits = GitLabFrontendDiscoveryLimits.defaults();
        var request = new GitLabFrontendScreenContextRequest(
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
            var source = sourceDiscoveryService.buildScreenContext(request);
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
            GitLabFrontendScreenSourceContext source,
            List<UiExplorerSectionModeAssignment> activeSections,
            GitLabFrontendDiscoveryLimits limits
    ) {
        var screen = source.screen();
        var files = source.sourceFiles().stream()
                .map(file -> new UiExplorerSourceContextFile(
                        file.path(),
                        file.roles().stream().map(Enum::name).toList(),
                        file.content(),
                        file.returnedCharacters(),
                        file.truncated()
                ))
                .toList();
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
                        diagnostic.code(),
                        diagnostic.message(),
                        diagnostic.sourcePath()
                ))
                .toList();
        var boundary = new UiExplorerSourceContextBoundary(
                source.repositoryFileCount(),
                source.scannedRouteFileCount(),
                files.size(),
                source.totalReturnedCharacters(),
                source.inventoryTruncated(),
                source.routeCatalogTruncated(),
                source.truncated(),
                limits.maxInventoryFiles(),
                limits.maxRouteFiles(),
                limits.maxRouteEntries(),
                limits.maxContextFiles(),
                limits.maxFileCharacters(),
                limits.maxTotalCharacters(),
                limits.maxTraversalDepth()
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
                        screen.screenId(),
                        screen.label(),
                        screen.routePattern(),
                        screen.parentRoutePattern()
                ),
                screen.status().name(),
                screen.lazyLoaded(),
                screen.guards(),
                screen.routeParameters(),
                screen.limitations(),
                screen.routeSource() != null
                        ? new UiExplorerSourceReference(
                                null,
                                screen.routeSource().path(),
                                screen.routeSource().symbol(),
                                screen.routeSource().startLine(),
                                screen.routeSource().endLine()
                        )
                        : null,
                new UiExplorerSourceRevision(
                        source.sourceRevision().ref(),
                        source.sourceRevision().commitId()
                ),
                overallStatus(files, sectionCoverage, source),
                files,
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
            GitLabFrontendScreenSourceContext source
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
                && (source.truncated()
                || source.screen().status() != GitLabFrontendDiscoveryStatus.RESOLVED
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
            List<UiExplorerSourceContextFile> files,
            List<UiExplorerSectionContextCoverage> coverage,
            GitLabFrontendScreenSourceContext source
    ) {
        if (files.isEmpty()) {
            return UiExplorerCoverageStatus.BLOCKED;
        }
        var status = coverage.stream()
                .map(UiExplorerSectionContextCoverage::status)
                .reduce(UiExplorerCoverageStatus.READY, this::mergeStatus);
        var materialDiagnostic = source.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.severity() != GitLabFrontendDiagnosticSeverity.INFO);
        if (status == UiExplorerCoverageStatus.READY
                && (source.truncated()
                || source.screen().status() != GitLabFrontendDiscoveryStatus.RESOLVED
                || materialDiagnostic)) {
            return UiExplorerCoverageStatus.PARTIAL;
        }
        return status;
    }

    private List<String> visibilityLimits(GitLabFrontendScreenSourceContext source) {
        var limits = new ArrayList<String>();
        limits.add("Static discovery does not execute TypeScript or runtime form definitions.");
        limits.add("Organizational libraries outside the resolved repository scope may remain unavailable.");
        if (source.truncated()) {
            limits.add("The source snapshot reached an inventory, route, file, character or traversal limit.");
        }
        if (source.screen().status() != GitLabFrontendDiscoveryStatus.RESOLVED) {
            limits.add("The selected route-to-view mapping is not fully unambiguous.");
        }
        return List.copyOf(limits);
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
