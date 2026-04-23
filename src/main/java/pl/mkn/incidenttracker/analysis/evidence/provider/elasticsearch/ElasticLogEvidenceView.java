package pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;

import java.util.ArrayList;
import java.util.List;

public record ElasticLogEvidenceView(
        List<LogEntry> entries
) {

    public static final AnalysisEvidenceReference EVIDENCE_REFERENCE =
            new AnalysisEvidenceReference("elasticsearch", "logs");
    static final String ATTRIBUTE_TIMESTAMP = "timestamp";
    static final String ATTRIBUTE_LEVEL = "level";
    static final String ATTRIBUTE_SERVICE_NAME = "serviceName";
    static final String ATTRIBUTE_CLASS_NAME = "className";
    static final String ATTRIBUTE_MESSAGE = "message";
    static final String ATTRIBUTE_EXCEPTION = "exception";
    static final String ATTRIBUTE_THREAD = "thread";
    static final String ATTRIBUTE_SPAN_ID = "spanId";
    static final String ATTRIBUTE_NAMESPACE = "namespace";
    static final String ATTRIBUTE_POD_NAME = "podName";
    static final String ATTRIBUTE_CONTAINER_NAME = "containerName";
    static final String ATTRIBUTE_CONTAINER_IMAGE = "containerImage";
    static final String ATTRIBUTE_MESSAGE_TRUNCATED = "messageTruncated";
    static final String ATTRIBUTE_EXCEPTION_TRUNCATED = "exceptionTruncated";

    public ElasticLogEvidenceView {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public static ElasticLogEvidenceView from(AnalysisContext context) {
        return from(context.evidenceSections());
    }

    public static ElasticLogEvidenceView from(AnalysisEvidenceSection section) {
        return from(List.of(section));
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
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_TIMESTAMP),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_LEVEL),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SERVICE_NAME),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CLASS_NAME),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MESSAGE),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_EXCEPTION),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_THREAD),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SPAN_ID),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_NAMESPACE),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_POD_NAME),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CONTAINER_NAME),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CONTAINER_IMAGE),
                        Boolean.parseBoolean(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MESSAGE_TRUNCATED)),
                        Boolean.parseBoolean(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_EXCEPTION_TRUNCATED))
                ));
            }
        }

        return new ElasticLogEvidenceView(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public static boolean matches(AnalysisEvidenceSection section) {
        return EVIDENCE_REFERENCE.provider().equals(section.provider())
                && EVIDENCE_REFERENCE.category().equals(section.category());
    }

    public String toMarkdown() {
        var lines = new ArrayList<String>();
        lines.add("Elasticsearch log evidence");
        lines.add("");

        if (entries.isEmpty()) {
            lines.add("- no Elasticsearch log entries were attached for this incident.");
            return String.join(System.lineSeparator(), lines) + System.lineSeparator();
        }

        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            if (index > 0) {
                lines.add("");
            }

            lines.add(renderHeading(index, entry));
            lines.add("");
            addInlineLine(lines, "timestamp", entry.timestamp());
            addInlineLine(lines, "service", entry.serviceName());
            addInlineLine(lines, "class", entry.className());
            addInlineLine(lines, "thread", entry.thread());
            addInlineLine(lines, "span id", entry.spanId());
            addInlineLine(lines, "namespace", entry.namespace());
            addInlineLine(lines, "pod", entry.podName());
            addInlineLine(lines, "container", entry.containerName());
            addInlineLine(lines, "image", entry.containerImage());

            if (entry.messageTruncated()) {
                lines.add("- message truncated: `true`");
            }
            if (entry.exceptionTruncated()) {
                lines.add("- exception truncated: `true`");
            }

            addTextBlock(lines, "message", entry.message());
            addTextBlock(lines, "exception", entry.exception());
        }

        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    private String renderHeading(int index, LogEntry entry) {
        var parts = new ArrayList<String>();
        parts.add("Log entry `" + (index + 1) + "`");

        if (hasText(entry.level())) {
            parts.add("`" + escapeInlineCode(entry.level()) + "`");
        }
        if (hasText(entry.serviceName())) {
            parts.add("`" + escapeInlineCode(entry.serviceName()) + "`");
        } else if (hasText(entry.title())) {
            parts.add("`" + escapeInlineCode(entry.title()) + "`");
        }

        return String.join(" ", parts);
    }

    private void addInlineLine(List<String> lines, String label, String value) {
        if (!hasText(value)) {
            return;
        }

        lines.add("- " + label + ": `" + escapeInlineCode(value) + "`");
    }

    private void addTextBlock(List<String> lines, String label, String value) {
        if (!hasText(value)) {
            return;
        }

        lines.add("- " + label + ":");
        lines.add("```text");
        lines.add(value);
        lines.add("```");
    }

    private String escapeInlineCode(String value) {
        return value.replace('`', '\'');
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
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
            String containerImage,
            boolean messageTruncated,
            boolean exceptionTruncated
    ) {
    }

}
