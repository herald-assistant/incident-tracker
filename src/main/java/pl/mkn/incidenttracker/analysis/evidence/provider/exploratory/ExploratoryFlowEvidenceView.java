package pl.mkn.incidenttracker.analysis.evidence.provider.exploratory;

import pl.mkn.incidenttracker.analysis.AnalysisFlowDiagram;
import pl.mkn.incidenttracker.analysis.AnalysisFlowDiagramEdge;
import pl.mkn.incidenttracker.analysis.AnalysisFlowDiagramMetadata;
import pl.mkn.incidenttracker.analysis.AnalysisFlowDiagramNode;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ExploratoryFlowEvidenceView(
        List<FlowNode> nodes,
        List<FlowEdge> edges
) {

    public static final AnalysisEvidenceReference EVIDENCE_REFERENCE =
            new AnalysisEvidenceReference("exploratory-flow", "reconstructed-flow");

    public static ExploratoryFlowEvidenceView from(AnalysisContext context) {
        return from(context.evidenceSections());
    }

    public static ExploratoryFlowEvidenceView from(List<AnalysisEvidenceSection> sections) {
        var nodes = new ArrayList<FlowNode>();
        var edges = new ArrayList<FlowEdge>();

        for (var section : sections) {
            if (!matches(section)) {
                continue;
            }

            for (var item : section.items()) {
                var attributes = AnalysisEvidenceAttributes.byName(item.attributes());
                var kind = AnalysisEvidenceAttributes.text(attributes, "kind");
                if ("NODE".equals(kind)) {
                    nodes.add(new FlowNode(
                            AnalysisEvidenceAttributes.text(attributes, "id"),
                            AnalysisEvidenceAttributes.text(attributes, "nodeKind"),
                            AnalysisEvidenceAttributes.text(attributes, "title"),
                            AnalysisEvidenceAttributes.text(attributes, "componentName"),
                            AnalysisEvidenceAttributes.text(attributes, "factStatus"),
                            AnalysisEvidenceAttributes.text(attributes, "firstSeenAt"),
                            parseMetadata(attributes),
                            Boolean.parseBoolean(AnalysisEvidenceAttributes.text(attributes, "errorSource"))
                    ));
                } else if ("EDGE".equals(kind)) {
                    edges.add(new FlowEdge(
                            AnalysisEvidenceAttributes.text(attributes, "id"),
                            AnalysisEvidenceAttributes.text(attributes, "fromNodeId"),
                            AnalysisEvidenceAttributes.text(attributes, "toNodeId"),
                            parseInteger(attributes, "sequence"),
                            AnalysisEvidenceAttributes.text(attributes, "interactionType"),
                            AnalysisEvidenceAttributes.text(attributes, "factStatus"),
                            AnalysisEvidenceAttributes.text(attributes, "startedAt"),
                            parseLong(attributes, "durationMs"),
                            AnalysisEvidenceAttributes.text(attributes, "supportSummary")
                    ));
                }
            }
        }

        var sortedEdges = edges.stream()
                .sorted(Comparator.comparingInt(FlowEdge::sequence))
                .toList();
        return new ExploratoryFlowEvidenceView(List.copyOf(nodes), sortedEdges);
    }

    public boolean isEmpty() {
        return nodes.isEmpty() && edges.isEmpty();
    }

    public AnalysisFlowDiagram toDiagram() {
        return new AnalysisFlowDiagram(
                nodes.stream()
                        .map(node -> new AnalysisFlowDiagramNode(
                                node.id(),
                                node.kind(),
                                node.title(),
                                node.componentName(),
                                node.factStatus(),
                                node.firstSeenAt(),
                                node.metadata(),
                                node.errorSource()
                        ))
                        .toList(),
                edges.stream()
                        .map(edge -> new AnalysisFlowDiagramEdge(
                                edge.id(),
                                edge.fromNodeId(),
                                edge.toNodeId(),
                                edge.sequence(),
                                edge.interactionType(),
                                edge.factStatus(),
                                edge.startedAt(),
                                edge.durationMs(),
                                edge.supportSummary()
                        ))
                        .toList()
        );
    }

    private static boolean matches(AnalysisEvidenceSection section) {
        return EVIDENCE_REFERENCE.provider().equals(section.provider())
                && EVIDENCE_REFERENCE.category().equals(section.category());
    }

    private static List<AnalysisFlowDiagramMetadata> parseMetadata(java.util.Map<String, String> attributes) {
        var metadata = new ArrayList<AnalysisFlowDiagramMetadata>();

        for (var entry : attributes.entrySet()) {
            if (!entry.getKey().startsWith("metadata.")) {
                continue;
            }

            metadata.add(new AnalysisFlowDiagramMetadata(
                    entry.getKey().substring("metadata.".length()),
                    entry.getValue()
            ));
        }

        return List.copyOf(metadata);
    }

    private static int parseInteger(java.util.Map<String, String> attributes, String name) {
        var rawValue = AnalysisEvidenceAttributes.text(attributes, name);
        if (rawValue == null) {
            return 0;
        }

        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static Long parseLong(java.util.Map<String, String> attributes, String name) {
        var rawValue = AnalysisEvidenceAttributes.text(attributes, name);
        if (rawValue == null) {
            return null;
        }

        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record FlowNode(
            String id,
            String kind,
            String title,
            String componentName,
            String factStatus,
            String firstSeenAt,
            List<AnalysisFlowDiagramMetadata> metadata,
            boolean errorSource
    ) {
    }

    public record FlowEdge(
            String id,
            String fromNodeId,
            String toNodeId,
            int sequence,
            String interactionType,
            String factStatus,
            String startedAt,
            Long durationMs,
            String supportSummary
    ) {
    }

}
