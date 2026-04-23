package pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record GitLabResolvedCodeEvidenceReadableView(
        List<ResolvedCodeItem> items
) {

    public static final AnalysisEvidenceReference EVIDENCE_REFERENCE =
            new AnalysisEvidenceReference("gitlab", "resolved-code");

    static final String ATTRIBUTE_ENVIRONMENT = "environment";
    static final String ATTRIBUTE_BRANCH = "branch";
    static final String ATTRIBUTE_GROUP = "group";
    static final String ATTRIBUTE_PROJECT_NAME = "projectName";
    static final String ATTRIBUTE_FILE_PATH = "filePath";
    static final String ATTRIBUTE_REFERENCE_TYPE = "referenceType";
    static final String ATTRIBUTE_SYMBOL = "symbol";
    static final String ATTRIBUTE_RAW_REFERENCE = "rawReference";
    static final String ATTRIBUTE_LINE_NUMBER = "lineNumber";
    static final String ATTRIBUTE_RESOLVE_SCORE = "resolveScore";
    static final String ATTRIBUTE_REQUESTED_START_LINE = "requestedStartLine";
    static final String ATTRIBUTE_REQUESTED_END_LINE = "requestedEndLine";
    static final String ATTRIBUTE_RETURNED_START_LINE = "returnedStartLine";
    static final String ATTRIBUTE_RETURNED_END_LINE = "returnedEndLine";
    static final String ATTRIBUTE_TOTAL_LINES = "totalLines";
    static final String ATTRIBUTE_CONTENT = "content";
    static final String ATTRIBUTE_CONTENT_TRUNCATED = "contentTruncated";

    public GitLabResolvedCodeEvidenceReadableView {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public static GitLabResolvedCodeEvidenceReadableView from(AnalysisContext context) {
        return from(context.evidenceSections());
    }

    public static GitLabResolvedCodeEvidenceReadableView from(List<AnalysisEvidenceSection> evidenceSections) {
        return evidenceSections.stream()
                .filter(GitLabResolvedCodeEvidenceReadableView::matches)
                .findFirst()
                .map(GitLabResolvedCodeEvidenceReadableView::from)
                .orElseGet(GitLabResolvedCodeEvidenceReadableView::empty);
    }

    public static GitLabResolvedCodeEvidenceReadableView from(AnalysisEvidenceSection section) {
        if (!matches(section)) {
            return empty();
        }

        var items = new ArrayList<ResolvedCodeItem>();
        for (var item : section.items()) {
            var attributes = AnalysisEvidenceAttributes.byName(item.attributes());
            items.add(new ResolvedCodeItem(
                    item.title(),
                    AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_ENVIRONMENT),
                    AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_BRANCH),
                    AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_GROUP),
                    AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PROJECT_NAME),
                    AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_FILE_PATH),
                    AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_REFERENCE_TYPE),
                    AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SYMBOL),
                    AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_RAW_REFERENCE),
                    parseInteger(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_LINE_NUMBER)),
                    parseInteger(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_RESOLVE_SCORE)),
                    parseInteger(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_REQUESTED_START_LINE)),
                    parseInteger(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_REQUESTED_END_LINE)),
                    parseInteger(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_RETURNED_START_LINE)),
                    parseInteger(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_RETURNED_END_LINE)),
                    parseInteger(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_TOTAL_LINES)),
                    AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CONTENT),
                    Boolean.parseBoolean(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CONTENT_TRUNCATED))
            ));
        }

        return new GitLabResolvedCodeEvidenceReadableView(items);
    }

    public static GitLabResolvedCodeEvidenceReadableView empty() {
        return new GitLabResolvedCodeEvidenceReadableView(List.of());
    }

    public static boolean matches(AnalysisEvidenceSection section) {
        return EVIDENCE_REFERENCE.provider().equals(section.provider())
                && EVIDENCE_REFERENCE.category().equals(section.category());
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public String toMarkdown() {
        var lines = new ArrayList<String>();
        lines.add("GitLab resolved code references");
        lines.add("");

        if (items.isEmpty()) {
            lines.add("- no deterministic GitLab file or code chunk was resolved from incident evidence.");
            return String.join(System.lineSeparator(), lines) + System.lineSeparator();
        }

        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            if (index > 0) {
                lines.add("");
            }

            lines.add(renderHeading(item));
            lines.add("");
            lines.add("- repository: `" + escapeInlineCode(normalizeReadableValue(item.projectName())) + "`");

            if (hasText(item.group())) {
                lines.add("- group: `" + escapeInlineCode(item.group()) + "`");
            }
            if (hasText(item.branch())) {
                lines.add("- branch: `" + escapeInlineCode(item.branch()) + "`");
            }
            if (hasText(item.environment())) {
                lines.add("- environment: `" + escapeInlineCode(item.environment()) + "`");
            }

            var referenceSummary = renderReferenceSummary(item);
            if (hasText(referenceSummary)) {
                lines.add("- reference: " + referenceSummary);
            }

            var lineWindowSummary = renderLineWindowSummary(item);
            if (hasText(lineWindowSummary)) {
                lines.add("- returned lines: " + lineWindowSummary);
            }

            if (item.resolveScore() != null) {
                lines.add("- resolve score: `" + item.resolveScore() + "`");
            }

            if (item.contentTruncated()) {
                lines.add("- content truncated: `true`");
            }

            if (hasText(item.content())) {
                lines.add("");
                lines.add(codeFence(item.filePath(), item.content()));
                lines.add(item.content());
                lines.add(codeFence(item.filePath(), item.content()));
            }
        }

        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    private static Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String renderHeading(ResolvedCodeItem item) {
        var filePath = hasText(item.filePath()) ? item.filePath() : item.title();
        var projectName = hasText(item.projectName()) ? item.projectName() : null;
        if (hasText(projectName)) {
            return "Resolved file `" + escapeInlineCode(projectName) + "` `" + escapeInlineCode(filePath) + "`";
        }

        return "Resolved file `" + escapeInlineCode(normalizeReadableValue(filePath)) + "`";
    }

    private String renderReferenceSummary(ResolvedCodeItem item) {
        var parts = new ArrayList<String>();
        if (hasText(item.referenceType())) {
            parts.add("`" + escapeInlineCode(item.referenceType()) + "`");
        }
        if (hasText(item.symbol())) {
            parts.add("`" + escapeInlineCode(item.symbol()) + "`");
        } else if (hasText(item.rawReference())
                && !sameIgnoringWhitespace(item.rawReference(), item.filePath())) {
            parts.add("`" + escapeInlineCode(shortLabel(item.rawReference(), 120)) + "`");
        }
        if (item.lineNumber() != null) {
            parts.add("around line `" + item.lineNumber() + "`");
        }

        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private String renderLineWindowSummary(ResolvedCodeItem item) {
        if (item.returnedStartLine() == null || item.returnedEndLine() == null) {
            return null;
        }

        var rendered = new StringBuilder()
                .append('`')
                .append(item.returnedStartLine())
                .append('-')
                .append(item.returnedEndLine())
                .append('`');

        if (item.totalLines() != null) {
            rendered.append(" of `").append(item.totalLines()).append('`');
        }

        if (item.requestedStartLine() != null
                && item.requestedEndLine() != null
                && (!item.requestedStartLine().equals(item.returnedStartLine())
                || !item.requestedEndLine().equals(item.returnedEndLine()))) {
            rendered.append(" (requested `")
                    .append(item.requestedStartLine())
                    .append('-')
                    .append(item.requestedEndLine())
                    .append("`)");
        }

        return rendered.toString();
    }

    private String codeFence(String filePath, String content) {
        var fence = content != null && content.contains("```") ? "````" : "```";
        var language = languageFor(filePath);
        return hasText(language) ? fence + language : fence;
    }

    private String languageFor(String filePath) {
        if (!hasText(filePath) || !filePath.contains(".")) {
            return "";
        }

        var extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "java" -> "java";
            case "kt" -> "kotlin";
            case "groovy" -> "groovy";
            case "sql" -> "sql";
            case "xml" -> "xml";
            case "json" -> "json";
            case "yml", "yaml" -> "yaml";
            default -> "";
        };
    }

    private String normalizeReadableValue(String value) {
        return hasText(value) ? value : "unknown";
    }

    private String shortLabel(String value, int maxLength) {
        if (!hasText(value) || value.length() <= maxLength) {
            return normalizeReadableValue(value);
        }

        return value.substring(0, maxLength) + "...";
    }

    private boolean sameIgnoringWhitespace(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return false;
        }

        return left.trim().equals(right.trim());
    }

    private String escapeInlineCode(String value) {
        return value.replace('`', '\'');
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    public record ResolvedCodeItem(
            String title,
            String environment,
            String branch,
            String group,
            String projectName,
            String filePath,
            String referenceType,
            String symbol,
            String rawReference,
            Integer lineNumber,
            Integer resolveScore,
            Integer requestedStartLine,
            Integer requestedEndLine,
            Integer returnedStartLine,
            Integer returnedEndLine,
            Integer totalLines,
            String content,
            boolean contentTruncated
    ) {
    }
}
