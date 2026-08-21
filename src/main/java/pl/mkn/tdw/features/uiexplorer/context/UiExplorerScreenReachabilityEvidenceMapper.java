package pl.mkn.tdw.features.uiexplorer.context;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.List;

@Component
public class UiExplorerScreenReachabilityEvidenceMapper {

    private static final String PROVIDER = "ui-explorer";

    public List<AnalysisEvidenceSection> map(UiExplorerScreenReachabilityContext context) {
        var sections = new ArrayList<AnalysisEvidenceSection>();
        sections.add(screenSection(context));
        sections.add(routeChainSection(context));
        sections.add(componentSection(context));
        sections.add(dependencySection(context));
        sections.add(coverageSection(context));
        sections.add(boundarySection(context));
        if (!context.graph().diagnostics().isEmpty()) {
            sections.add(diagnosticSection(context));
        }
        return List.copyOf(sections);
    }

    private AnalysisEvidenceSection screenSection(UiExplorerScreenReachabilityContext context) {
        var screen = context.screen();
        return new AnalysisEvidenceSection(PROVIDER, "selected-screen", List.of(new AnalysisEvidenceItem(
                screen.routePattern(),
                List.of(
                        attribute("screenId", screen.screenId()),
                        attribute("component", screen.label()),
                        attribute("navigationContext", screen.navigationContext()),
                        attribute("discoveryStatus", context.screenDiscoveryStatus()),
                        attribute("lazyLoaded", context.lazyLoaded()),
                        attribute("guards", String.join(", ", context.guards())),
                        attribute("routeParameters", String.join(", ", context.routeParameters())),
                        attribute("branch", context.sourceRevision().branch()),
                        attribute("sourceRevision", context.sourceRevision().revision()),
                        attribute("reachabilityStatus", context.status())
                )
        )));
    }

    private AnalysisEvidenceSection routeChainSection(UiExplorerScreenReachabilityContext context) {
        var items = context.graph().effectiveRouteChain().segments().stream()
                .map(segment -> new AnalysisEvidenceItem(
                        segment.routePattern(),
                        List.of(
                                attribute("pathSegment", segment.pathSegment()),
                                attribute("outlet", segment.outlet()),
                                attribute("sourcePath", segment.source().path()),
                                attribute("sourceSymbol", segment.source().symbol())
                        )
                ))
                .toList();
        return new AnalysisEvidenceSection(PROVIDER, "route-chain", items);
    }

    private AnalysisEvidenceSection componentSection(UiExplorerScreenReachabilityContext context) {
        var items = context.components().stream()
                .map(component -> new AnalysisEvidenceItem(
                        component.symbol(),
                        List.of(
                                attribute("breadthFirstOrder", component.breadthFirstOrder()),
                                attribute("depth", component.depth()),
                                attribute("discoveryKind", component.discoveryKind()),
                                attribute("selector", component.selector()),
                                attribute("sourcePath", component.sourcePath()),
                                attribute("templatePath", component.templatePath()),
                                attribute("status", component.status()),
                                attribute("entrySymbols", component.entrySymbols().stream()
                                        .map(candidate -> candidate.symbolName()).distinct().toList()),
                                attribute("dependencyCount", component.dependencyIds().size()),
                                attribute("childCount", component.childComponentIds().size()),
                                attribute("sliceCharacters", component.returnedCharacters())
                        )
                ))
                .toList();
        return new AnalysisEvidenceSection(PROVIDER, "component-reachability", items);
    }

    private AnalysisEvidenceSection dependencySection(UiExplorerScreenReachabilityContext context) {
        var items = context.graph().dependencies().stream()
                .map(dependency -> new AnalysisEvidenceItem(
                        dependency.symbol(),
                        List.of(
                                attribute("discoveryOrder", dependency.discoveryOrder()),
                                attribute("kind", dependency.kind()),
                                attribute("category", dependency.category()),
                                attribute("sourcePath", dependency.sourcePath()),
                                attribute("moduleSpecifier", dependency.moduleSpecifier()),
                                attribute("status", dependency.status()),
                                attribute("methods", dependency.methods()),
                                attribute("usedBy", dependency.usedBy()),
                                attribute("downstreamCount", dependency.downstreamDependencyIds().size()),
                                attribute("sliceCharacters", dependency.returnedCharacters())
                        )
                ))
                .toList();
        return new AnalysisEvidenceSection(PROVIDER, "dependency-reachability", items);
    }

    private AnalysisEvidenceSection coverageSection(UiExplorerScreenReachabilityContext context) {
        var items = context.sectionCoverage().stream()
                .map(coverage -> new AnalysisEvidenceItem(
                        coverage.sectionId().label(),
                        List.of(
                                attribute("sectionId", coverage.sectionId()),
                                attribute("mode", coverage.mode()),
                                attribute("status", coverage.status()),
                                attribute("sourceCategories", coverage.sourceCategories()),
                                attribute("detail", coverage.detail())
                        )
                ))
                .toList();
        return new AnalysisEvidenceSection(PROVIDER, "section-coverage", items);
    }

    private AnalysisEvidenceSection boundarySection(UiExplorerScreenReachabilityContext context) {
        var boundary = context.boundary();
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        attributes.add(attribute("routeSegmentCount", boundary.routeSegmentCount()));
        attributes.add(attribute("componentCount", boundary.componentCount()));
        attributes.add(attribute("dependencyCount", boundary.dependencyCount()));
        attributes.add(attribute("edgeCount", boundary.edgeCount()));
        attributes.add(attribute("sourceFileCount", boundary.sourceFileCount()));
        attributes.add(attribute("sourceCharacters", boundary.sourceCharacters()));
        attributes.add(attribute("sliceCharacters", boundary.sliceCharacters()));
        attributes.add(attribute("outlineCharacters", boundary.outlineCharacters()));
        attributes.add(attribute("contextLimitReached", boundary.contextLimitReached()));
        context.researchGaps().forEach(gap -> attributes.add(attribute("researchGap", gap)));
        return new AnalysisEvidenceSection(PROVIDER, "reachability-boundary", List.of(new AnalysisEvidenceItem(
                "Deterministic BFS reachability", attributes
        )));
    }

    private AnalysisEvidenceSection diagnosticSection(UiExplorerScreenReachabilityContext context) {
        var items = context.graph().diagnostics().stream()
                .map(diagnostic -> new AnalysisEvidenceItem(
                        diagnostic.code().name(),
                        List.of(
                                attribute("severity", diagnostic.severity()),
                                attribute("message", diagnostic.message()),
                                attribute("sourcePath", diagnostic.source() != null ? diagnostic.source().path() : null)
                        )
                ))
                .toList();
        return new AnalysisEvidenceSection(PROVIDER, "reachability-diagnostics", items);
    }

    private AnalysisEvidenceAttribute attribute(String name, Object value) {
        if (value instanceof List<?> list) {
            return new AnalysisEvidenceAttribute(name, list.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", ")));
        }
        return new AnalysisEvidenceAttribute(name, value != null ? value.toString() : "");
    }
}
