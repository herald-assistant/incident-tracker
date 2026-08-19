package pl.mkn.tdw.features.uiexplorer.context;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerScreenIdentity;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;

import java.util.List;

public record UiExplorerSourceContextSnapshot(
        String systemId,
        String systemLabel,
        UiExplorerSourceContextScope sourceScope,
        UiExplorerScreenIdentity screen,
        String screenDiscoveryStatus,
        boolean lazyLoaded,
        List<String> guards,
        List<String> routeParameters,
        List<String> screenLimitations,
        UiExplorerSourceReference routeSource,
        UiExplorerSourceRevision sourceRevision,
        UiExplorerCoverageStatus status,
        List<UiExplorerSourceContextFile> sourceFiles,
        List<UiExplorerSourceContextSignal> technicalSignals,
        List<UiExplorerSectionContextCoverage> sectionCoverage,
        List<UiExplorerSourceContextDiagnostic> diagnostics,
        UiExplorerSourceContextBoundary boundary,
        List<String> visibilityLimits
) {

    public UiExplorerSourceContextSnapshot {
        guards = guards != null ? List.copyOf(guards) : List.of();
        routeParameters = routeParameters != null ? List.copyOf(routeParameters) : List.of();
        screenLimitations = screenLimitations != null ? List.copyOf(screenLimitations) : List.of();
        sourceFiles = sourceFiles != null ? List.copyOf(sourceFiles) : List.of();
        technicalSignals = technicalSignals != null ? List.copyOf(technicalSignals) : List.of();
        sectionCoverage = sectionCoverage != null ? List.copyOf(sectionCoverage) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
