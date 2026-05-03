package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog.HandoffRule;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class OperationalContextMarkdownParser {

    private static final Pattern ENTRY_HEADING = Pattern.compile("^###\\s+(.+?)\\s*$");
    private static final Pattern SECTION_HEADING = Pattern.compile("^##\\s+(.+?)\\s*$");
    private static final Set<String> SIGNAL_FIELDS = Set.of(
            "signals",
            "signal",
            "typical evidence signals",
            "rest endpoints",
            "rest operations",
            "endpoints",
            "hikaripool",
            "error markers",
            "error",
            "queues",
            "exchange",
            "exchanges",
            "agreement exchanges",
            "agreement queues",
            "decision exchange",
            "mq channels",
            "mq consumers",
            "events",
            "hosts",
            "host",
            "marker",
            "markers",
            "package",
            "packages",
            "controller",
            "controllers",
            "service",
            "services",
            "key classes",
            "classes",
            "entity",
            "entities",
            "feign client",
            "feign clients",
            "db table",
            "db tables",
            "schema",
            "schemas",
            "constants"
    );
    private static final Set<String> REFERENCE_FIELDS = Set.of(
            "canonical references",
            "library",
            "libraries",
            "module",
            "modules",
            "groupid",
            "version",
            "access",
            "protocol",
            "types",
            "values",
            "enum value",
            "key fields",
            "sub-entities",
            "jobs",
            "rating paths",
            "valuation statuses",
            "valuation actions",
            "supported product types",
            "simulation types",
            "clp->sf statuses",
            "sf->clp statuses"
    );

    List<GlossaryTerm> parseGlossary(String markdown) {
        var terms = new ArrayList<GlossaryTerm>();

        for (var entry : parseEntries(markdown)) {
            terms.add(new GlossaryTerm(
                    entry.id(),
                    firstValue(entry, "term", entry.title()),
                    firstValue(entry, "category", entry.category()),
                    entry.scalarFields().get("definition"),
                    valuesFor(entry, "use in our context", "context"),
                    valuesFor(entry, "do not confuse with", "not to confuse with"),
                    signalValues(entry),
                    referenceValues(entry),
                    valuesFor(entry, "synonyms"),
                    noteValues(entry)
            ));
        }

        return List.copyOf(terms);
    }

    List<HandoffRule> parseHandoffRules(String markdown) {
        var rules = new ArrayList<HandoffRule>();

        for (var entry : parseEntries(markdown)) {
            rules.add(new HandoffRule(
                    entry.id(),
                    firstValue(entry, "title", entry.id()),
                    entry.scalarFields().getOrDefault("route to", ""),
                    valuesFor(entry, "use when"),
                    valuesFor(entry, "do not use when"),
                    valuesFor(entry, "required evidence"),
                    valuesFor(entry, "expected first action"),
                    valuesFor(entry, "partner teams"),
                    valuesFor(entry, "notes")
            ));
        }

        rules.addAll(parseHandoffTables(markdown, rules.stream()
                .map(HandoffRule::id)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll)));

        return List.copyOf(rules);
    }

    private List<HandoffRule> parseHandoffTables(String markdown, Set<String> usedIds) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }

        var rules = new ArrayList<HandoffRule>();
        var currentCategory = "General";
        var headers = List.<String>of();
        var skippingOpenQuestions = false;

        for (var line : markdown.split("\\R")) {
            var trimmedLine = line.trim();
            var sectionMatcher = SECTION_HEADING.matcher(trimmedLine);
            if (sectionMatcher.matches()) {
                var sectionTitle = stripMarkdown(sectionMatcher.group(1));
                skippingOpenQuestions = sectionTitle.toLowerCase(Locale.ROOT).startsWith("open questions");
                headers = List.of();
                if (!skippingOpenQuestions) {
                    currentCategory = sectionTitle;
                }
                continue;
            }

            if (skippingOpenQuestions) {
                continue;
            }

            if (!isTableRow(trimmedLine)) {
                headers = List.of();
                continue;
            }

            var cells = tableCells(trimmedLine);
            if (cells.isEmpty() || isTableSeparator(cells)) {
                continue;
            }

            if (headers.isEmpty()) {
                var normalizedHeaders = cells.stream()
                        .map(this::normalizeFieldLabel)
                        .toList();
                if (normalizedHeaders.contains("symptom")
                        && normalizedHeaders.contains("route to")
                        && normalizedHeaders.contains("required evidence")) {
                    headers = normalizedHeaders;
                }
                continue;
            }

            var row = tableRow(headers, cells);
            var symptom = row.getOrDefault("symptom", "");
            var routeTo = row.getOrDefault("route to", "");
            var requiredEvidence = splitValues(row.getOrDefault("required evidence", ""));
            if (!StringUtils.hasText(symptom) || !StringUtils.hasText(routeTo)) {
                continue;
            }

            var id = uniqueId(slug(currentCategory + "-" + symptom), usedIds);
            rules.add(new HandoffRule(
                    id,
                    symptom,
                    routeTo,
                    List.of(symptom),
                    List.of(),
                    requiredEvidence,
                    List.of("Route to " + routeTo + "."),
                    partnerTeams(routeTo),
                    List.of("Parsed from `" + currentCategory + "` markdown table.")
            ));
        }

        return List.copyOf(rules);
    }

    private boolean isTableRow(String line) {
        return line.startsWith("|") && line.endsWith("|");
    }

    private boolean isTableSeparator(List<String> cells) {
        return cells.stream().allMatch(cell -> cell.replace(":", "").replace("-", "").isBlank());
    }

    private List<String> tableCells(String line) {
        var trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return List.of(trimmed.split("\\|", -1)).stream()
                .map(this::stripMarkdown)
                .map(String::trim)
                .toList();
    }

    private Map<String, String> tableRow(List<String> headers, List<String> cells) {
        var row = new LinkedHashMap<String, String>();
        for (var index = 0; index < headers.size() && index < cells.size(); index++) {
            row.put(headers.get(index), cells.get(index));
        }
        return row;
    }

    private List<String> partnerTeams(String routeTo) {
        if (!StringUtils.hasText(routeTo)) {
            return List.of();
        }
        var normalized = routeTo.toLowerCase(Locale.ROOT);
        if (normalized.contains("owner of ") || normalized.contains("integration owner")) {
            return List.of();
        }
        return List.of(routeTo);
    }

    private List<MarkdownEntry> parseEntries(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }

        var entries = new ArrayList<MarkdownEntry>();
        var usedIds = new LinkedHashSet<String>();
        var currentCategory = "General";
        String currentTitle = null;
        var currentLines = new ArrayList<String>();
        var skippingOpenQuestions = false;

        for (var line : markdown.split("\\R")) {
            var trimmedLine = line.trim();
            var sectionMatcher = SECTION_HEADING.matcher(trimmedLine);
            if (sectionMatcher.matches()) {
                if (currentTitle != null) {
                    entries.add(parseEntry(currentTitle, currentCategory, currentLines, usedIds));
                    currentTitle = null;
                    currentLines = new ArrayList<>();
                }

                var sectionTitle = stripMarkdown(sectionMatcher.group(1));
                skippingOpenQuestions = sectionTitle.toLowerCase(Locale.ROOT).startsWith("open questions");
                if (!skippingOpenQuestions) {
                    currentCategory = sectionTitle;
                }
                continue;
            }

            if (skippingOpenQuestions) {
                continue;
            }

            var matcher = ENTRY_HEADING.matcher(trimmedLine);
            if (matcher.matches()) {
                if (currentTitle != null) {
                    entries.add(parseEntry(currentTitle, currentCategory, currentLines, usedIds));
                }

                currentTitle = stripHeadingMarker(matcher.group(1));
                currentLines = new ArrayList<>();
                continue;
            }

            if (currentTitle != null) {
                currentLines.add(line);
            }
        }

        if (currentTitle != null && currentLines.stream().anyMatch(StringUtils::hasText)) {
            entries.add(parseEntry(currentTitle, currentCategory, currentLines, usedIds));
        }

        return List.copyOf(entries);
    }

    private MarkdownEntry parseEntry(String title, String category, List<String> lines, Set<String> usedIds) {
        var scalarFields = new LinkedHashMap<String, String>();
        var listFields = new LinkedHashMap<String, List<String>>();
        String currentSection = null;

        for (var line : lines) {
            var trimmedLine = line.trim();
            if (!StringUtils.hasText(trimmedLine)) {
                continue;
            }

            var fieldLine = stripListMarker(trimmedLine);
            if (fieldLine.startsWith("**") && fieldLine.contains(":")) {
                var scalarField = parseScalarField(fieldLine);
                if (scalarField != null) {
                    if (StringUtils.hasText(scalarField.value())) {
                        scalarFields.putIfAbsent(scalarField.label(), scalarField.value());
                        listFields.computeIfAbsent(scalarField.label(), key -> new ArrayList<>())
                                .addAll(splitValues(scalarField.value()));
                        currentSection = null;
                    } else {
                        currentSection = scalarField.label();
                        listFields.putIfAbsent(currentSection, new ArrayList<>());
                    }
                    continue;
                }
            }

            if (fieldLine.startsWith("**") && fieldLine.endsWith("**")) {
                currentSection = normalizeFieldLabel(fieldLine.substring(2, fieldLine.length() - 2));
                listFields.putIfAbsent(currentSection, new ArrayList<>());
                continue;
            }

            if (currentSection == null) {
                continue;
            }

            listFields.computeIfAbsent(currentSection, key -> new ArrayList<>())
                    .add(stripMarkdown(stripListMarker(trimmedLine)));
        }

        var id = uniqueId(slug(title), usedIds);
        return new MarkdownEntry(id, title, category, Map.copyOf(scalarFields), immutableLists(listFields));
    }

    private ScalarField parseScalarField(String line) {
        var colonInsideBold = line.indexOf(":**", 2);
        if (colonInsideBold > 1) {
            var label = normalizeFieldLabel(line.substring(2, colonInsideBold));
            var value = stripMarkdown(line.substring(colonInsideBold + 3).trim());
            return new ScalarField(label, value);
        }

        var colonOutsideBold = line.indexOf("**:", 2);
        if (colonOutsideBold > 1) {
            var label = normalizeFieldLabel(line.substring(2, colonOutsideBold));
            var value = stripMarkdown(line.substring(colonOutsideBold + 3).trim());
            return new ScalarField(label, value);
        }

        return null;
    }

    private Map<String, List<String>> immutableLists(Map<String, List<String>> source) {
        var values = new LinkedHashMap<String, List<String>>();
        source.forEach((key, value) -> values.put(key, distinct(value)));
        return Map.copyOf(values);
    }

    private String firstValue(MarkdownEntry entry, String field, String fallback) {
        var value = entry.scalarFields().get(field);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private List<String> valuesFor(MarkdownEntry entry, String... fields) {
        var values = new ArrayList<String>();
        for (var field : fields) {
            values.addAll(entry.listFields().getOrDefault(field, List.of()));
        }
        return distinct(values);
    }

    private List<String> signalValues(MarkdownEntry entry) {
        var values = new ArrayList<String>();
        for (var field : SIGNAL_FIELDS) {
            values.addAll(entry.listFields().getOrDefault(field, List.of()));
        }
        return distinct(values);
    }

    private List<String> referenceValues(MarkdownEntry entry) {
        var values = new ArrayList<String>();
        for (var field : REFERENCE_FIELDS) {
            values.addAll(entry.listFields().getOrDefault(field, List.of()));
        }
        return distinct(values);
    }

    private List<String> noteValues(MarkdownEntry entry) {
        var notes = new ArrayList<String>();
        notes.addAll(entry.listFields().getOrDefault("notes", List.of()));
        notes.addAll(entry.listFields().getOrDefault("note", List.of()));
        for (var field : entry.listFields().entrySet()) {
            if (Set.of("term", "category", "definition", "use in our context", "context", "do not confuse with",
                    "not to confuse with", "synonyms", "notes", "note").contains(field.getKey())) {
                continue;
            }
            if (SIGNAL_FIELDS.contains(field.getKey()) || REFERENCE_FIELDS.contains(field.getKey())) {
                continue;
            }
            for (var value : field.getValue()) {
                notes.add(label(field.getKey()) + ": " + value);
            }
        }
        return distinct(notes);
    }

    private List<String> splitValues(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }

        var normalized = stripMarkdown(value);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        return List.of(normalized.split("\\s*,\\s*|\\s*;\\s*")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values.stream()
                .map(this::stripMarkdown)
                .filter(StringUtils::hasText)
                .toList()));
    }

    private String stripMarkdown(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        return value.trim()
                .replace("`", "")
                .replace("&rarr;", "->")
                .replace("→", "->")
                .replaceAll("\\\\([\\\\`*_{}\\[\\]()#+\\-.!>])", "$1");
    }

    private String stripHeadingMarker(String value) {
        var stripped = stripMarkdown(value);
        if (stripped.startsWith("`") && stripped.endsWith("`") && stripped.length() > 1) {
            return stripped.substring(1, stripped.length() - 1);
        }
        return stripped;
    }

    private String stripListMarker(String value) {
        var stripped = value.trim();
        while (stripped.startsWith("- ") || stripped.startsWith("* ")) {
            stripped = stripped.substring(2).trim();
        }
        return stripped;
    }

    private String normalizeFieldLabel(String value) {
        return stripMarkdown(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String slug(String value) {
        var withoutAccents = Normalizer.normalize(stripMarkdown(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        var slug = withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return StringUtils.hasText(slug) ? slug : "entry";
    }

    private String uniqueId(String baseId, Set<String> usedIds) {
        var candidate = baseId;
        var suffix = 2;
        while (usedIds.contains(candidate)) {
            candidate = baseId + "-" + suffix;
            suffix++;
        }
        usedIds.add(candidate);
        return candidate;
    }

    private String label(String field) {
        if (!StringUtils.hasText(field)) {
            return "";
        }
        var words = field.split("\\s+");
        var labelled = new StringBuilder();
        for (var word : words) {
            if (labelled.length() > 0) {
                labelled.append(' ');
            }
            labelled.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return labelled.toString();
    }

    private record MarkdownEntry(
            String id,
            String title,
            String category,
            Map<String, String> scalarFields,
            Map<String, List<String>> listFields
    ) {
    }

    private record ScalarField(
            String label,
            String value
    ) {
    }

}
