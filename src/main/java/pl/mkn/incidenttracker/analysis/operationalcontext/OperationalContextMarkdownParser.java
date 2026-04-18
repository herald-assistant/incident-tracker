package pl.mkn.incidenttracker.analysis.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.analysis.operationalcontext.OperationalContextCatalog.HandoffRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class OperationalContextMarkdownParser {

    private static final Pattern ENTRY_HEADING = Pattern.compile("^###\\s+`([^`]+)`\\s*$");

    List<GlossaryTerm> parseGlossary(String markdown) {
        var terms = new ArrayList<GlossaryTerm>();

        for (var entry : parseEntries(markdown)) {
            terms.add(new GlossaryTerm(
                    entry.id(),
                    entry.scalarFields().getOrDefault("term", entry.id()),
                    stripMarkdown(entry.scalarFields().get("category")),
                    entry.scalarFields().get("definition"),
                    entry.listFields().getOrDefault("use in our context", List.of()),
                    entry.listFields().getOrDefault("do not confuse with", List.of()),
                    entry.listFields().getOrDefault("typical evidence signals", List.of()),
                    stripMarkdownList(entry.listFields().getOrDefault("canonical references", List.of())),
                    stripMarkdownList(entry.listFields().getOrDefault("synonyms", List.of())),
                    entry.listFields().getOrDefault("notes", List.of())
            ));
        }

        return List.copyOf(terms);
    }

    List<HandoffRule> parseHandoffRules(String markdown) {
        var rules = new ArrayList<HandoffRule>();

        for (var entry : parseEntries(markdown)) {
            rules.add(new HandoffRule(
                    entry.id(),
                    entry.scalarFields().getOrDefault("title", entry.id()),
                    entry.scalarFields().getOrDefault("route to", ""),
                    entry.listFields().getOrDefault("use when", List.of()),
                    entry.listFields().getOrDefault("do not use when", List.of()),
                    stripMarkdownList(entry.listFields().getOrDefault("required evidence", List.of())),
                    entry.listFields().getOrDefault("expected first action", List.of()),
                    stripMarkdownList(entry.listFields().getOrDefault("partner teams", List.of())),
                    entry.listFields().getOrDefault("notes", List.of())
            ));
        }

        return List.copyOf(rules);
    }

    private List<MarkdownEntry> parseEntries(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }

        var entries = new ArrayList<MarkdownEntry>();
        String currentId = null;
        var currentLines = new ArrayList<String>();

        for (var line : markdown.split("\\R")) {
            var trimmedLine = line.trim();
            if (trimmedLine.startsWith("## Open Questions")) {
                if (currentId != null) {
                    entries.add(parseEntry(currentId, currentLines));
                }
                break;
            }

            var matcher = ENTRY_HEADING.matcher(trimmedLine);
            if (matcher.matches()) {
                if (currentId != null) {
                    entries.add(parseEntry(currentId, currentLines));
                }

                currentId = matcher.group(1);
                currentLines = new ArrayList<>();
                continue;
            }

            if (currentId != null) {
                currentLines.add(line);
            }
        }

        if (currentId != null && currentLines.stream().anyMatch(StringUtils::hasText)) {
            entries.add(parseEntry(currentId, currentLines));
        }

        return List.copyOf(entries);
    }

    private MarkdownEntry parseEntry(String id, List<String> lines) {
        var scalarFields = new LinkedHashMap<String, String>();
        var listFields = new LinkedHashMap<String, List<String>>();
        String currentSection = null;

        for (var line : lines) {
            var trimmedLine = line.trim();
            if (!StringUtils.hasText(trimmedLine)) {
                continue;
            }

            if (trimmedLine.startsWith("**") && trimmedLine.contains(":")) {
                var scalarField = parseScalarField(trimmedLine);
                if (scalarField != null) {
                    scalarFields.put(scalarField.label(), scalarField.value());
                    currentSection = null;
                    continue;
                }
            }

            if (trimmedLine.startsWith("**") && trimmedLine.endsWith("**")) {
                currentSection = trimmedLine.substring(2, trimmedLine.length() - 2)
                        .trim()
                        .toLowerCase(Locale.ROOT);
                listFields.putIfAbsent(currentSection, new ArrayList<>());
                continue;
            }

            if (currentSection == null) {
                continue;
            }

            listFields.computeIfAbsent(currentSection, key -> new ArrayList<>())
                    .add(stripMarkdown(trimmedLine.startsWith("- ")
                            ? trimmedLine.substring(2).trim()
                            : trimmedLine));
        }

        return new MarkdownEntry(id, Map.copyOf(scalarFields), immutableLists(listFields));
    }

    private ScalarField parseScalarField(String line) {
        var colonInsideBold = line.indexOf(":**", 2);
        if (colonInsideBold > 1) {
            var label = line.substring(2, colonInsideBold).trim().toLowerCase(Locale.ROOT);
            var value = stripMarkdown(line.substring(colonInsideBold + 3).trim());
            return new ScalarField(label, value);
        }

        var colonOutsideBold = line.indexOf("**:", 2);
        if (colonOutsideBold > 1) {
            var label = line.substring(2, colonOutsideBold).trim().toLowerCase(Locale.ROOT);
            var value = stripMarkdown(line.substring(colonOutsideBold + 3).trim());
            return new ScalarField(label, value);
        }

        return null;
    }

    private Map<String, List<String>> immutableLists(Map<String, List<String>> source) {
        var values = new LinkedHashMap<String, List<String>>();
        source.forEach((key, value) -> values.put(key, List.copyOf(value)));
        return Map.copyOf(values);
    }

    private List<String> stripMarkdownList(List<String> values) {
        return values.stream()
                .map(this::stripMarkdown)
                .toList();
    }

    private String stripMarkdown(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        return value.trim().replace("`", "");
    }

    private record MarkdownEntry(
            String id,
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
