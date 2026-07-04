package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextReferences;

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
            "match signals",
            "exact signals",
            "strong signals",
            "medium signals",
            "weak signals"
    );
    private static final Set<String> REFERENCE_FIELDS = Set.of(
            "canonical references",
            "related terms"
    );

    List<OperationalContextGlossaryTerm> parseGlossary(String markdown) {
        var terms = new ArrayList<OperationalContextGlossaryTerm>();

        for (var entry : parseEntries(markdown)) {
            if ("gaps".equals(entry.category().toLowerCase(Locale.ROOT))) {
                continue;
            }
            terms.add(new OperationalContextGlossaryTerm(
                    entry.id(),
                    firstValue(entry, "term", entry.title()),
                    firstValue(entry, "category", entry.category()),
                    entry.scalarFields().get("definition"),
                    valuesFor(entry, "local meaning and boundaries"),
                    valuesFor(entry, "not to confuse with"),
                    signalValues(entry),
                    referenceValues(entry),
                    valuesFor(entry, "aliases"),
                    noteValues(entry)
            ));
        }

        return List.copyOf(terms);
    }

    List<OperationalContextHandoffRule> parseHandoffRules(String markdown) {
        var rules = new ArrayList<OperationalContextHandoffRule>();

        for (var entry : parseEntries(markdown)) {
            if (!isHandoffRuleEntry(entry)) {
                continue;
            }
            rules.add(new OperationalContextHandoffRule(
                    entry.id(),
                    firstValue(entry, "title", entry.id()),
                    routeTo(entry),
                    valuesFor(entry, "applies when", "use when", "trigger condition"),
                    valuesFor(entry, "does not apply when"),
                    valuesFor(entry, "required evidence"),
                    valuesFor(entry, "expected first actions", "expected first action", "recommended first actions"),
                    partnerTeams(entry),
                    operationalContextLinks(entry),
                    valuesFor(entry, "notes", "llm tool hints", "limitations")
            ));
        }

        return List.copyOf(rules);
    }

    private boolean isHandoffRuleEntry(MarkdownEntry entry) {
        return !"gaps".equals(entry.category().toLowerCase(Locale.ROOT))
                && !entry.scalarFields().containsKey("gap id")
                && (entry.scalarFields().containsKey("rule id") || entry.scalarFields().containsKey("title"));
    }

    private String routeTo(MarkdownEntry entry) {
        var candidateTeams = firstDefined(first(valuesFor(entry, "candidate teams")), routeDecisionValue(entry, "candidate teams"));
        if (StringUtils.hasText(candidateTeams)) {
            return candidateTeams;
        }

        var externalParties = firstDefined(first(valuesFor(entry, "external parties")), routeDecisionValue(entry, "external parties"));
        if (StringUtils.hasText(externalParties)) {
            return externalParties;
        }

        var scalarRouteTo = entry.scalarFields().get("route to");
        if (StringUtils.hasText(scalarRouteTo)) {
            return scalarRouteTo;
        }

        return routingRoleTarget(entry);
    }

    private List<String> partnerTeams(MarkdownEntry entry) {
        var partners = new ArrayList<String>();
        partners.addAll(valuesFor(entry, "partner teams"));

        var routeDecisionPartners = firstDefined(first(valuesFor(entry, "partner teams")), routeDecisionValue(entry, "partner teams"));
        if (StringUtils.hasText(routeDecisionPartners)) {
            partners.addAll(splitValues(routeDecisionPartners));
        }

        return distinct(partners);
    }

    private String routeDecisionValue(MarkdownEntry entry, String label) {
        var normalizedLabel = normalizeFieldLabel(label);
        for (var value : valuesFor(entry, "route decision")) {
            var separator = value.indexOf(':');
            if (separator < 1) {
                continue;
            }
            var currentLabel = normalizeFieldLabel(value.substring(0, separator));
            if (!normalizedLabel.equals(currentLabel)) {
                continue;
            }
            var currentValue = value.substring(separator + 1).trim();
            if (!isNone(currentValue)) {
                return currentValue;
            }
        }
        return "";
    }

    private String routingRoleTarget(MarkdownEntry entry) {
        for (var line : valuesFor(entry, "routing roles")) {
            if (!line.contains("|")) {
                continue;
            }
            var cells = tableCells(line);
            if (cells.size() < 2 || "role".equalsIgnoreCase(cells.get(0))) {
                continue;
            }
            var role = cells.get(0);
            var target = cells.get(1);
            if ((role.equals("first-responder") || role.equals("current-handler")) && !isNone(target)) {
                return target;
            }
        }
        return "";
    }

    private boolean isNone(String value) {
        return !StringUtils.hasText(value)
                || "none".equalsIgnoreCase(value.trim())
                || "null".equalsIgnoreCase(value.trim());
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

    private List<MarkdownEntry> parseEntries(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }

        var entries = new ArrayList<MarkdownEntry>();
        var usedIds = new LinkedHashSet<String>();
        var currentCategory = "General";
        String currentTitle = null;
        var currentLines = new ArrayList<String>();
        var skippingGaps = false;

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
                skippingGaps = sectionTitle.toLowerCase(Locale.ROOT).startsWith("gaps");
                if (!skippingGaps) {
                    currentCategory = sectionTitle;
                }
                continue;
            }

            if (skippingGaps) {
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

    private String first(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private String firstDefined(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
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
            values.addAll(cleanSignalValues(entry.listFields().getOrDefault(field, List.of())));
        }
        return distinct(values);
    }

    private List<String> cleanSignalValues(List<String> values) {
        return values.stream()
                .map(this::stripMarkdown)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(value -> !isNone(value))
                .filter(value -> !isMatchSignalBucketLabel(value))
                .toList();
    }

    private boolean isMatchSignalBucketLabel(String value) {
        var colonIdx = value.indexOf(':');
        if (colonIdx > 0) {
            var prefix = normalizeFieldLabel(value.substring(0, colonIdx).trim());
            if (Set.of("exact", "strong", "medium", "weak").contains(prefix)) {
                return true;
            }
        }
        var normalized = normalizeFieldLabel(value.replaceAll(":$", ""));
        return Set.of("exact", "strong", "medium", "weak").contains(normalized);
    }

    private List<String> referenceValues(MarkdownEntry entry) {
        var values = new ArrayList<String>();
        for (var field : REFERENCE_FIELDS) {
            values.addAll(entry.listFields().getOrDefault(field, List.of()));
        }
        return distinct(values).stream()
                .filter(value -> !isNone(value))
                .toList();
    }

    private OperationalContextReferences operationalContextLinks(MarkdownEntry entry) {
        var systems = new ArrayList<String>();
        var repositories = new ArrayList<String>();
        var processes = new ArrayList<String>();
        var boundedContexts = new ArrayList<String>();
        var integrations = new ArrayList<String>();
        var terms = new ArrayList<String>();
        var teams = new ArrayList<String>();
        var handoffRules = new ArrayList<String>();

        for (var value : valuesFor(entry, "operational context links", "context links")) {
            var reference = typedReference(value);
            if (reference == null) {
                continue;
            }
            switch (reference.type()) {
                case "system" -> systems.add(reference.id());
                case "repository" -> repositories.add(reference.id());
                case "process" -> processes.add(reference.id());
                case "bounded-context" -> boundedContexts.add(reference.id());
                case "integration" -> integrations.add(reference.id());
                case "term" -> terms.add(reference.id());
                case "team" -> teams.add(reference.id());
                case "handoff-rule" -> handoffRules.add(reference.id());
                default -> {
                }
            }
        }

        return new OperationalContextReferences(
                distinct(systems),
                distinct(repositories),
                distinct(processes),
                distinct(boundedContexts),
                distinct(integrations),
                distinct(terms),
                distinct(teams),
                distinct(handoffRules)
        );
    }

    private TypedReference typedReference(String value) {
        if (!StringUtils.hasText(value) || isNone(value)) {
            return null;
        }

        var normalized = stripMarkdown(value).trim();
        var separator = normalized.indexOf(':');
        if (separator < 1) {
            return null;
        }

        var type = normalizeReferenceType(normalized.substring(0, separator));
        if (!StringUtils.hasText(type)) {
            return null;
        }

        var id = normalized.substring(separator + 1).trim()
                .replaceFirst("\\s+.*$", "")
                .replaceAll("[,.;]+$", "");
        if (!StringUtils.hasText(id)) {
            return null;
        }

        return new TypedReference(type, id);
    }

    private String normalizeReferenceType(String value) {
        var normalized = normalizeFieldLabel(value)
                .replace("_", "-")
                .replace(" ", "-");
        return switch (normalized) {
            case "system", "systems" -> "system";
            case "repository", "repositories", "repo", "repos" -> "repository";
            case "process", "processes" -> "process";
            case "bounded-context", "bounded-contexts", "boundedcontext", "boundedcontexts" -> "bounded-context";
            case "integration", "integrations" -> "integration";
            case "term", "terms", "glossary-term", "glossary-terms" -> "term";
            case "team", "teams" -> "team";
            case "handoff-rule", "handoff-rules", "handoffrule", "handoffrules" -> "handoff-rule";
            default -> null;
        };
    }

    private List<String> noteValues(MarkdownEntry entry) {
        var notes = new ArrayList<String>();
        notes.addAll(entry.listFields().getOrDefault("notes", List.of()));
        for (var field : entry.listFields().entrySet()) {
            if (Set.of("term", "category", "definition", "local meaning and boundaries",
                    "not to confuse with", "aliases", "notes").contains(field.getKey())) {
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
        stripped = stripped.replaceFirst("^\\d+\\.\\s+", "");
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

    private record TypedReference(
            String type,
            String id
    ) {
    }

}
