package pl.mkn.incidenttracker.analysis.evidence.view;

import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ElasticLogEvidenceView(
        List<LogEntry> entries
) {

    public static final AnalysisEvidenceReference EVIDENCE_REFERENCE =
            new AnalysisEvidenceReference("elasticsearch", "logs");

    public static ElasticLogEvidenceView from(AnalysisContext context) {
        return from(context.evidenceSections());
    }

    public static ElasticLogEvidenceView from(List<AnalysisEvidenceSection> evidenceSections) {
        var entries = new ArrayList<LogEntry>();

        for (var section : evidenceSections) {
            if (!matches(section)) {
                continue;
            }

            for (var item : section.items()) {
                var attributes = AnalysisEvidenceAttributes.byName(item.attributes());
                entries.add(new LogEntry(
                        item.title(),
                        AnalysisEvidenceAttributes.text(attributes, "timestamp"),
                        AnalysisEvidenceAttributes.text(attributes, "level"),
                        AnalysisEvidenceAttributes.text(attributes, "serviceName"),
                        AnalysisEvidenceAttributes.text(attributes, "className"),
                        AnalysisEvidenceAttributes.text(attributes, "message"),
                        AnalysisEvidenceAttributes.text(attributes, "exception"),
                        AnalysisEvidenceAttributes.text(attributes, "thread"),
                        AnalysisEvidenceAttributes.text(attributes, "spanId"),
                        AnalysisEvidenceAttributes.text(attributes, "namespace"),
                        AnalysisEvidenceAttributes.text(attributes, "podName"),
                        AnalysisEvidenceAttributes.text(attributes, "containerName"),
                        AnalysisEvidenceAttributes.text(attributes, "containerImage")
                ));
            }
        }

        return new ElasticLogEvidenceView(List.copyOf(entries));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private static boolean matches(AnalysisEvidenceSection section) {
        return EVIDENCE_REFERENCE.provider().equals(section.provider())
                && EVIDENCE_REFERENCE.category().equals(section.category());
    }

    public record LogEntry(
            String title,
            String timestamp,
            String level,
            String serviceName,
            String className,
            String message,
            String exception,
            String thread,
            String spanId,
            String namespace,
            String podName,
            String containerName,
            String containerImage
    ) {

        public Map<String, String> asAttributes() {
            var values = new java.util.LinkedHashMap<String, String>();
            values.put("timestamp", timestamp);
            values.put("level", level);
            values.put("serviceName", serviceName);
            values.put("className", className);
            values.put("message", message);
            values.put("exception", exception);
            values.put("thread", thread);
            values.put("spanId", spanId);
            values.put("namespace", namespace);
            values.put("podName", podName);
            values.put("containerName", containerName);
            values.put("containerImage", containerImage);
            return Map.copyOf(values);
        }
    }

}
