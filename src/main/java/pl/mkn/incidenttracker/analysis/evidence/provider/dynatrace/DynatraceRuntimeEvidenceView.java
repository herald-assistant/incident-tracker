package pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record DynatraceRuntimeEvidenceView(
        CollectionStatusItem collectionStatus,
        List<ComponentStatusItem> componentStatuses
) {

    public static final AnalysisEvidenceReference EVIDENCE_REFERENCE =
            new AnalysisEvidenceReference("dynatrace", "runtime-signals");

    static final String ITEM_TYPE_ATTRIBUTE = "dynatraceItemType";
    static final String ITEM_TYPE_COLLECTION_STATUS = "collection-status";
    static final String ITEM_TYPE_COMPONENT_STATUS = "component-status";

    static final String ATTRIBUTE_COLLECTION_STATUS = "collectionStatus";
    static final String ATTRIBUTE_COLLECTION_REASON = "collectionReason";
    static final String ATTRIBUTE_INTERPRETATION = "interpretation";
    static final String ATTRIBUTE_CORRELATION_STATUS = "correlationStatus";
    static final String ATTRIBUTE_COMPONENT_NAME = "componentName";
    static final String ATTRIBUTE_COMPONENT_SIGNAL_STATUS = "componentSignalStatus";
    static final String ATTRIBUTE_PROBLEM_DISPLAY_ID = "problemDisplayId";
    static final String ATTRIBUTE_PROBLEM_TITLE = "problemTitle";
    static final String ATTRIBUTE_SIGNAL_CATEGORIES = "signalCategories";
    static final String ATTRIBUTE_CORRELATION_HIGHLIGHTS = "correlationHighlights";
    static final String ATTRIBUTE_SUMMARY = "summary";
    private static final Pattern HTTP_STATUS_CODE_PATTERN = Pattern.compile("\\bHTTP\\s+(\\d{3})\\b");

    public DynatraceRuntimeEvidenceView {
        componentStatuses = componentStatuses != null ? List.copyOf(componentStatuses) : List.of();
    }

    public static DynatraceRuntimeEvidenceView from(AnalysisContext context) {
        return from(context.evidenceSections());
    }

    public static DynatraceRuntimeEvidenceView from(List<AnalysisEvidenceSection> evidenceSections) {
        return evidenceSections.stream()
                .filter(DynatraceRuntimeEvidenceView::matches)
                .findFirst()
                .map(DynatraceRuntimeEvidenceView::from)
                .orElseGet(DynatraceRuntimeEvidenceView::empty);
    }

    public static DynatraceRuntimeEvidenceView from(AnalysisEvidenceSection section) {
        if (!matches(section)) {
            return empty();
        }

        CollectionStatusItem collectionStatus = null;
        var componentStatuses = new ArrayList<ComponentStatusItem>();

        for (var item : section.items()) {
            var attributes = AnalysisEvidenceAttributes.byName(item.attributes());
            var itemType = AnalysisEvidenceAttributes.text(attributes, ITEM_TYPE_ATTRIBUTE);

            if (ITEM_TYPE_COLLECTION_STATUS.equals(itemType)) {
                collectionStatus = new CollectionStatusItem(
                        CollectionStatus.from(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_COLLECTION_STATUS)),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_COLLECTION_REASON),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_INTERPRETATION),
                        CorrelationStatus.from(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CORRELATION_STATUS))
                );
                continue;
            }

            if (ITEM_TYPE_COMPONENT_STATUS.equals(itemType)) {
                componentStatuses.add(new ComponentStatusItem(
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_COMPONENT_NAME),
                        CorrelationStatus.from(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CORRELATION_STATUS)),
                        ComponentSignalStatus.from(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_COMPONENT_SIGNAL_STATUS)),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_INTERPRETATION),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PROBLEM_DISPLAY_ID),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PROBLEM_TITLE),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SIGNAL_CATEGORIES)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CORRELATION_HIGHLIGHTS)),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SUMMARY)
                ));
            }
        }

        return new DynatraceRuntimeEvidenceView(collectionStatus, componentStatuses);
    }

    public static DynatraceRuntimeEvidenceView empty() {
        return new DynatraceRuntimeEvidenceView(null, List.of());
    }

    public static boolean matches(AnalysisEvidenceSection section) {
        return EVIDENCE_REFERENCE.provider().equals(section.provider())
                && EVIDENCE_REFERENCE.category().equals(section.category());
    }

    public boolean hasStructuredStatusSummary() {
        return collectionStatus != null || !componentStatuses.isEmpty();
    }

    public String toMarkdown() {
        var lines = new ArrayList<String>();
        lines.add("Dynatrace runtime signals");
        lines.add("");

        if (collectionStatus != null) {
            lines.add("- collection status: " + collectionStatus.status().name());

            if (hasText(collectionStatus.reason())
                    && collectionStatus.status() != CollectionStatus.COLLECTED) {
                lines.add("- reason: " + emphasizeHttpStatus(collectionStatus.reason()));
            }

            if (collectionStatus.correlationStatus() == CorrelationStatus.NO_MATCH) {
                lines.add("- correlation status: " + collectionStatus.correlationStatus().name());
            }

            if (hasText(collectionStatus.interpretation())
                    && collectionStatus.status() != CollectionStatus.COLLECTED) {
                lines.add("- interpretation: " + collectionStatus.interpretation());
            } else if (hasText(collectionStatus.interpretation())
                    && componentStatuses.isEmpty()
                    && collectionStatus.correlationStatus() == CorrelationStatus.NO_MATCH) {
                lines.add("- interpretation: " + collectionStatus.interpretation());
            }
        }

        for (var componentStatus : componentStatuses) {
            lines.add(renderComponentStatusLine(componentStatus));
        }

        if (!hasStructuredStatusSummary()) {
            lines.add("- collection status: UNKNOWN");
            lines.add("- interpretation: Dynatrace runtime signals were attached without a structured status summary.");
        }

        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    private static List<String> splitValues(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }

        var separator = rawValue.contains("||") ? "\\|\\|" : ",";
        var values = new ArrayList<String>();
        for (var token : rawValue.split(separator)) {
            if (StringUtils.hasText(token)) {
                values.add(token.trim());
            }
        }

        return List.copyOf(values);
    }

    private String renderComponentStatusLine(ComponentStatusItem componentStatus) {
        var componentName = normalizeReadableValue(componentStatus.componentName());
        var correlationStatus = componentStatus.correlationStatus().name();
        var signalStatus = componentStatus.signalStatus().name();
        var rendered = new StringBuilder()
                .append("- component `")
                .append(escapeInlineCode(componentName))
                .append("`: ")
                .append(correlationStatus)
                .append(", ")
                .append(signalStatus)
                .append('.');

        if (componentStatus.signalStatus() == ComponentSignalStatus.NO_RELEVANT_SIGNALS) {
            if (hasText(componentStatus.interpretation())) {
                rendered.append(' ').append(componentStatus.interpretation());
            }
            return rendered.toString();
        }

        var problemDisplayId = componentStatus.problemDisplayId();
        var problemTitle = componentStatus.problemTitle();
        if (hasText(problemDisplayId) || hasText(problemTitle)) {
            rendered.append(" Problem ");
            if (hasText(problemDisplayId)) {
                rendered.append('`').append(escapeInlineCode(problemDisplayId)).append('`');
            }
            if (hasText(problemTitle)) {
                if (hasText(problemDisplayId)) {
                    rendered.append(' ');
                }
                rendered.append('`').append(escapeInlineCode(problemTitle)).append('`');
            }
            rendered.append('.');
        }

        var categories = formatMarkdownValueList(componentStatus.signalCategories());
        if (hasText(categories)) {
            rendered.append(" Categories: ").append(categories).append('.');
        }

        var highlights = formatMarkdownValueList(componentStatus.correlationHighlights());
        if (hasText(highlights)) {
            rendered.append(" Highlights: ").append(highlights).append('.');
        }

        if (hasText(componentStatus.summary())) {
            rendered.append(" Summary: ").append(componentStatus.summary()).append('.');
        }

        return rendered.toString();
    }

    private String formatMarkdownValueList(List<String> values) {
        if (values.isEmpty()) {
            return null;
        }

        return values.stream()
                .map(this::escapeInlineCode)
                .map("`%s`"::formatted)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    private String emphasizeHttpStatus(String value) {
        var matcher = HTTP_STATUS_CODE_PATTERN.matcher(value);
        if (!matcher.find()) {
            return value;
        }

        return matcher.replaceFirst("HTTP `$1`");
    }

    private String escapeInlineCode(String value) {
        return value.replace('`', '\'');
    }

    private String normalizeReadableValue(String value) {
        return hasText(value) ? value : "unknown";
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    public record CollectionStatusItem(
            CollectionStatus status,
            String reason,
            String interpretation,
            CorrelationStatus correlationStatus
    ) {
    }

    public record ComponentStatusItem(
            String componentName,
            CorrelationStatus correlationStatus,
            ComponentSignalStatus signalStatus,
            String interpretation,
            String problemDisplayId,
            String problemTitle,
            List<String> signalCategories,
            List<String> correlationHighlights,
            String summary
    ) {

        public ComponentStatusItem {
            signalCategories = signalCategories != null ? List.copyOf(signalCategories) : List.of();
            correlationHighlights = correlationHighlights != null ? List.copyOf(correlationHighlights) : List.of();
        }
    }

    public enum CollectionStatus {
        COLLECTED,
        UNAVAILABLE,
        DISABLED,
        SKIPPED,
        UNKNOWN;

        static CollectionStatus from(String value) {
            if (!StringUtils.hasText(value)) {
                return UNKNOWN;
            }

            try {
                return CollectionStatus.valueOf(value.trim());
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
    }

    public enum CorrelationStatus {
        MATCHED,
        NO_MATCH,
        UNKNOWN;

        static CorrelationStatus from(String value) {
            if (!StringUtils.hasText(value)) {
                return UNKNOWN;
            }

            try {
                return CorrelationStatus.valueOf(value.trim());
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
    }

    public enum ComponentSignalStatus {
        SIGNALS_PRESENT,
        NO_RELEVANT_SIGNALS,
        UNKNOWN;

        static ComponentSignalStatus from(String value) {
            if (!StringUtils.hasText(value)) {
                return UNKNOWN;
            }

            try {
                return ComponentSignalStatus.valueOf(value.trim());
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
    }
}
