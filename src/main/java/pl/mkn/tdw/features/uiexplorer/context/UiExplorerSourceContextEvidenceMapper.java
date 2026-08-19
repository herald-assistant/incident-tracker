package pl.mkn.tdw.features.uiexplorer.context;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.List;

@Component
public class UiExplorerSourceContextEvidenceMapper {

    private static final String PROVIDER = "ui-explorer";

    public List<AnalysisEvidenceSection> map(UiExplorerSourceContextSnapshot snapshot) {
        var sections = new ArrayList<AnalysisEvidenceSection>();
        sections.add(screenSection(snapshot));
        sections.add(manifestSection(snapshot));
        sections.add(signalSection(snapshot));
        sections.add(coverageSection(snapshot));
        sections.add(boundarySection(snapshot));
        if (!snapshot.diagnostics().isEmpty()) {
            sections.add(diagnosticSection(snapshot));
        }
        return List.copyOf(sections);
    }

    private AnalysisEvidenceSection screenSection(UiExplorerSourceContextSnapshot snapshot) {
        var screen = snapshot.screen();
        return new AnalysisEvidenceSection(PROVIDER, "selected-screen", List.of(new AnalysisEvidenceItem(
                screen.label(),
                List.of(
                        attribute("screenId", screen.screenId()),
                        attribute("routePattern", screen.routePattern()),
                        attribute("navigationContext", screen.navigationContext()),
                        attribute("discoveryStatus", snapshot.screenDiscoveryStatus()),
                        attribute("lazyLoaded", Boolean.toString(snapshot.lazyLoaded())),
                        attribute("guards", String.join(", ", snapshot.guards())),
                        attribute("routeParameters", String.join(", ", snapshot.routeParameters())),
                        attribute("branch", snapshot.sourceRevision().branch()),
                        attribute("sourceRevision", snapshot.sourceRevision().revision()),
                        attribute("contextStatus", snapshot.status().name())
                )
        )));
    }

    private AnalysisEvidenceSection manifestSection(UiExplorerSourceContextSnapshot snapshot) {
        var items = snapshot.sourceManifest().stream()
                .map(file -> new AnalysisEvidenceItem(
                        file.path(),
                        List.of(
                                attribute("roles", String.join(", ", file.roles())),
                                attribute("sourceCharacters", Integer.toString(file.sourceCharacters())),
                                attribute("semanticSlices", Integer.toString(file.sliceCount())),
                                attribute("contentSha256", file.contentSha256())
                        )
                ))
                .toList();
        return new AnalysisEvidenceSection(PROVIDER, "source-manifest", items);
    }

    private AnalysisEvidenceSection signalSection(UiExplorerSourceContextSnapshot snapshot) {
        var items = snapshot.technicalSignals().stream()
                .map(signal -> new AnalysisEvidenceItem(
                        signal.kind(),
                        List.of(
                                attribute("description", signal.description()),
                                attribute("confidence", signal.confidence()),
                                attribute("sourcePath", signal.source().path()),
                                attribute("sourceSymbol", signal.source().symbol()),
                                attribute("startLine", value(signal.source().startLine())),
                                attribute("endLine", value(signal.source().endLine()))
                        )
                ))
                .toList();
        return new AnalysisEvidenceSection(PROVIDER, "technical-signals", items);
    }

    private AnalysisEvidenceSection coverageSection(UiExplorerSourceContextSnapshot snapshot) {
        var items = snapshot.sectionCoverage().stream()
                .map(coverage -> new AnalysisEvidenceItem(
                        coverage.sectionId().label(),
                        List.of(
                                attribute("sectionId", coverage.sectionId().name()),
                                attribute("mode", coverage.mode().name()),
                                attribute("status", coverage.status().name()),
                                attribute("sourceCategories", String.join(", ", coverage.sourceCategories())),
                                attribute("detail", coverage.detail())
                        )
                ))
                .toList();
        return new AnalysisEvidenceSection(PROVIDER, "section-coverage", items);
    }

    private AnalysisEvidenceSection boundarySection(UiExplorerSourceContextSnapshot snapshot) {
        var boundary = snapshot.boundary();
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        attributes.add(attribute("visitedRouteNodeCount", Integer.toString(boundary.visitedRouteNodeCount())));
        attributes.add(attribute("visitedRouteFileCount", Integer.toString(boundary.visitedRouteFileCount())));
        attributes.add(attribute("graphSourceReadCount", Integer.toString(boundary.graphSourceReadCount())));
        attributes.add(attribute("aliasResolutionCount", Integer.toString(boundary.aliasResolutionCount())));
        attributes.add(attribute("unresolvedEdgeCount", Integer.toString(boundary.unresolvedEdgeCount())));
        attributes.add(attribute("returnedContextFileCount", Integer.toString(boundary.returnedContextFileCount())));
        attributes.add(attribute("totalReturnedCharacters", Integer.toString(boundary.totalReturnedCharacters())));
        attributes.add(attribute("graphLimitReached", Boolean.toString(boundary.graphLimitReached())));
        attributes.add(attribute("contextLimitReached", Boolean.toString(boundary.contextLimitReached())));
        attributes.add(attribute("maxRouteNodes", Integer.toString(boundary.maxRouteNodes())));
        attributes.add(attribute("maxRouteFiles", Integer.toString(boundary.maxRouteFiles())));
        attributes.add(attribute("maxSourceReads", Integer.toString(boundary.maxSourceReads())));
        attributes.add(attribute("maxAliasResolutions", Integer.toString(boundary.maxAliasResolutions())));
        attributes.add(attribute("maxImportDepth", Integer.toString(boundary.maxImportDepth())));
        attributes.add(attribute("maxComponentDepth", Integer.toString(boundary.maxComponentDepth())));
        attributes.add(attribute("maxContextFiles", Integer.toString(boundary.maxContextFiles())));
        attributes.add(attribute("maxFileCharacters", Integer.toString(boundary.maxFileCharacters())));
        attributes.add(attribute("maxTotalCharacters", Integer.toString(boundary.maxTotalCharacters())));
        snapshot.visibilityLimits().forEach(limit -> attributes.add(attribute("visibilityLimit", limit)));
        return new AnalysisEvidenceSection(PROVIDER, "source-boundary", List.of(new AnalysisEvidenceItem(
                "Applied deterministic source boundary",
                attributes
        )));
    }

    private AnalysisEvidenceSection diagnosticSection(UiExplorerSourceContextSnapshot snapshot) {
        var items = snapshot.diagnostics().stream()
                .map(diagnostic -> new AnalysisEvidenceItem(
                        diagnostic.code(),
                        List.of(
                                attribute("severity", diagnostic.severity()),
                                attribute("message", diagnostic.message()),
                                attribute("sourcePath", diagnostic.sourcePath())
                        )
                ))
                .toList();
        return new AnalysisEvidenceSection(PROVIDER, "source-diagnostics", items);
    }

    private AnalysisEvidenceAttribute attribute(String name, String value) {
        return new AnalysisEvidenceAttribute(name, value(value));
    }

    private String value(Object value) {
        return value != null ? value.toString() : "";
    }
}
