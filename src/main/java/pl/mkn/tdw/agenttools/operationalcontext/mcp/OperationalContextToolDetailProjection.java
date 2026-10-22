package pl.mkn.tdw.agenttools.operationalcontext.mcp;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextNavigation.Link;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static pl.mkn.tdw.agenttools.operationalcontext.mcp.OperationalContextToolDtos.*;

/** Model-facing presentation only; selection, ownership and authorization remain with their existing owners. */
final class OperationalContextToolDetailProjection {
    private static final int ITEMS = 8;
    private static final int SUMMARY_CHARS = 240;
    private static final List<String> SECTIONS = List.of("overview", "relations", "signals", "codeSearch",
            "handoff", "sourceCoverage", "openQuestions", "navigation", "navigationDetails");

    private OperationalContextToolDetailProjection() {}

    static OpctxEntityDetailResult project(String type, String id, String label, String summary, String purpose,
            Map<String, Map<String, Object>> source, List<OpctxOpenQuestion> questions, List<String> sourceRefs,
            List<Link> paths, List<String> include) {
        boolean focused = include != null && !include.isEmpty();
        var requested = focused ? requested(include) : defaults(type);
        var allNavigation = navigation(type, paths);
        var navigation = requested.contains("navigation")
                ? (focused ? allNavigation : allNavigation.stream().limit(ITEMS).toList()) : List.<OpctxNavigationTarget>of();
        var details = requested.contains("navigationDetails") ? paths : List.<Link>of();
        var returned = new LinkedHashMap<String, Integer>();
        var omitted = new LinkedHashMap<String, Integer>();
        var sections = new LinkedHashMap<String, Map<String, Object>>();
        for (var section : List.of("overview", "relations", "signals", "codeSearch", "handoff", "sourceCoverage")) {
            var full = source.getOrDefault(section, Map.of());
            if ("codeSearch".equals(section)) full = codeSearch(full, true);
            if (!requested.contains(section)) {
                sections.put(section, Map.of());
                continue;
            }
            var candidate = focused ? full : defaultSection(type, section, full);
            // Search boundaries are indivisible: only an index may be compacted.
            var value = focused || ("codeSearch".equals(section) && "codeSearchScope".equals(type))
                    ? candidate : compact(candidate, 0);
            sections.put(section, value);
            count(returned, omitted, section, leafCount(value), leafCount(candidate));
        }
        var openQuestions = requested.contains("openQuestions")
                ? (focused ? questions : questions.stream().limit(5).toList()) : List.<OpctxOpenQuestion>of();
        if (requested.contains("openQuestions")) count(returned, omitted, "openQuestions", openQuestions.size(), questions.size());
        if (requested.contains("navigation")) count(returned, omitted, "navigation", navigation.size(), allNavigation.size());
        if (requested.contains("navigationDetails")) count(returned, omitted, "navigationDetails", details.size(), paths.size());
        var available = SECTIONS.stream().filter(section -> switch (section) {
            case "navigation", "navigationDetails" -> !paths.isEmpty();
            case "openQuestions" -> !questions.isEmpty();
            default -> !source.getOrDefault(section, Map.of()).isEmpty();
        }).toList();
        var expansions = new LinkedHashSet<>(omitted.keySet());
        if (!focused && "index".equals(sections.get("codeSearch").get("detail"))
                && "available".equals(sections.get("codeSearch").get("status"))) expansions.add("codeSearch");
        if (focused && requested.contains("navigation") && !paths.isEmpty()) expansions.add("navigationDetails");
        var links = expansions.stream().map(section -> new OpctxToolLink("expand-" + section, "opctx_get_entity",
                Map.of("type", type, "id", id, "include", List.of(section)),
                "Read complete " + section + ("navigationDetails".equals(section) ? " paths and provenance." : "."))).toList();
        var truncation = new OpctxTruncation(!omitted.isEmpty(), omitted.isEmpty() ? null
                : "Use the matching include section to recover all omitted values.", returned, omitted);
        var affordances = new OpctxToolAffordances(focused ? "focused" : "default", links,
                available.stream().map(section -> "include=" + section).toList(), List.of(), List.of(),
                null, List.of(), truncation, List.of());
        boolean navigationRequested = requested.contains("navigation") || requested.contains("navigationDetails");
        return new OpctxEntityDetailResult(type, id, label, focused ? null : summary, focused ? null : purpose,
                sections.get("overview"), sections.get("relations"), sections.get("signals"), sections.get("codeSearch"),
                sections.get("handoff"), sections.get("sourceCoverage"), openQuestions,
                focused && requested.stream().noneMatch(section -> Set.of("overview", "relations", "signals", "handoff", "sourceCoverage", "openQuestions").contains(section))
                        ? List.of() : sourceRefs,
                navigation, navigationRequested ? allNavigation.size() : 0,
                navigationRequested ? paths.size() : 0, details, affordances);
    }

    private static Set<String> requested(List<String> include) {
        var result = new LinkedHashSet<String>();
        for (var section : include) {
            if (section == null || !SECTIONS.contains(section.trim())) throw new IllegalArgumentException("Unknown operational context include section: " + section);
            result.add(section.trim());
        }
        return result;
    }

    private static Set<String> defaults(String type) {
        var result = new LinkedHashSet<>(List.of("overview", "codeSearch", "navigation", "sourceCoverage", "openQuestions"));
        if ("codeSearchScope".equals(type)) result.remove("sourceCoverage");
        if (Set.of("system", "repository", "handoffRule").contains(type)) result.add("signals");
        if (Set.of("process", "integration").contains(type)) result.add("relations");
        if (Set.of("system", "repository", "process", "integration", "boundedContext", "handoffRule").contains(type)) result.add("handoff");
        return result;
    }

    private static Map<String, Object> defaultSection(String type, String section, Map<String, Object> full) {
        if ("codeSearch".equals(section)) return codeSearch(full, "codeSearchScope".equals(type));
        if ("sourceCoverage".equals(section)) return select(full, "status", "limitations");
        if ("handoff".equals(section) && !"handoffRule".equals(type)) {
            var ownership = map(full.get("resolvedOwnership"));
            return ownership.isEmpty() ? Map.of() : Map.of("resolvedOwnership",
                    select(ownership, "situationType", "primaryOwners", "partnerOwners", "handoffReason", "visibilityLimits"));
        }
        if ("signals".equals(section) && "repository".equals(type)) return select(full, "llmToolHints");
        if ("relations".equals(section) && "process".equals(type)) {
            var result = new LinkedHashMap<>(select(full, "participants"));
            if (full.get("steps") instanceof List<?> steps && !steps.isEmpty()) result.put("steps", steps.stream()
                    .map(step -> select(map(step), "id", "name", "summary", "references")).toList());
            return result;
        }
        if ("relations".equals(section) && "integration".equals(type)) return select(full, "participants");
        if (!"overview".equals(section)) return full;
        return switch (type) {
            case "system" -> select(full, "systemType", "systemSubtype", "lifecycleStatus", "operationalStatus", "criticality", "runtime", "aliases");
            case "repository" -> select(full, "repositoryType", "lifecycleStatus", "criticality", "git");
            case "codeSearchScope" -> select(full, "scopeType", "lifecycleStatus", "useFor");
            case "process" -> {
                var result = new LinkedHashMap<>(select(full, "criticality", "lifecycleStatus", "useFor", "processBoundary", "completionSignals", "dataAndArtifacts"));
                var lifecycle = select(map(full.get("lifecycle")), "statuses", "terminalStates");
                if (!lifecycle.isEmpty()) result.put("lifecycle", lifecycle);
                yield result;
            }
            case "boundedContext" -> select(full, "contextType", "lifecycleStatus", "localLanguageSummary", "scope", "semanticBoundary");
            case "integration" -> select(full, "category", "integrationStyle", "flowDirection", "lifecycleStatus", "criticality");
            case "team" -> select(full, "lifecycleStatus", "useFor");
            case "glossaryTerm" -> select(full, "category", "useInContext", "doNotConfuseWith", "notes");
            case "handoffRule" -> Map.of();
            default -> throw new IllegalArgumentException("Unknown operational context entity type: " + type);
        };
    }

    private static Map<String, Object> codeSearch(Map<String, Object> full, boolean complete) {
        var result = new LinkedHashMap<>(full);
        var scopes = (List<?>) full.getOrDefault("codeSearchScopes", List.of());
        result.put("scopeTotal", scopes.size());
        result.put("detail", complete ? "complete" : "index");
        if (!complete) {
            result.put("codeSearchScopes", scopes.stream().map(scope -> {
                var item = select(map(scope), "id", "name", "target");
                var card = new LinkedHashMap<>(item);
                card.put("read", new OpctxToolLink("scope", "opctx_get_entity", Map.of("type", "codeSearchScope", "id", item.get("id")),
                        "Read repository roles, searchMode, pathPrefixes and limitations."));
                return card;
            }).toList());
            // Scope limitations are read with their boundaries; catalog findings stay visible.
            result.remove("limitations");
        }
        return result;
    }

    private static List<OpctxNavigationTarget> navigation(String originType, List<Link> paths) {
        var byTarget = new LinkedHashMap<String, List<Link>>();
        for (var path : paths) byTarget.computeIfAbsent(path.target().key().value(), ignored -> new ArrayList<>()).add(path);
        return byTarget.values().stream().sorted(Comparator
                .comparing((List<Link> group) -> group.stream().allMatch(Link::indirect))
                .thenComparingInt(group -> priority(originType, group))
                .thenComparing(group -> group.get(0).target().key().value()))
                .map(group -> {
                    var target = group.get(0).target();
                    var relations = new LinkedHashMap<RelationKey, Integer>();
                    for (var path : group) {
                        var roles = path.path().stream().filter(edge -> edge.role() != null && !edge.role().isBlank())
                                .map(edge -> new OpctxNavigationRole(edge.relationType(), edge.role())).toList();
                        relations.merge(new RelationKey(path.relationType(), path.direction(), path.indirect(), roles), 1, Integer::sum);
                    }
                    var summaries = relations.entrySet().stream().map(entry -> new OpctxNavigationRelation(entry.getKey().type(),
                            entry.getKey().direction(), entry.getKey().indirect(), entry.getKey().roles(), entry.getValue())).toList();
                    String type = modelType(target.type());
                    String text = target.summary();
                    boolean shortened = text != null && text.length() > SUMMARY_CHARS;
                    if (shortened) text = text.substring(0, SUMMARY_CHARS).stripTrailing() + "…";
                    return new OpctxNavigationTarget(type, target.id(), target.label(), text, shortened, summaries, group.size(),
                            new OpctxToolLink("related-entity", "opctx_get_entity", Map.of("type", type, "id", target.id()), purpose(type)));
                }).toList();
    }

    private static int priority(String origin, List<Link> group) {
        var type = modelType(group.get(0).target().type());
        if ("process".equals(origin) && group.stream().anyMatch(link -> link.path().stream()
                .anyMatch(edge -> "primary-system".equals(edge.relationType())))) return -1;
        var order = switch (origin) {
            case "process" -> List.of("boundedContext", "system", "integration", "codeSearchScope", "process", "glossaryTerm", "team", "repository");
            case "boundedContext" -> List.of("codeSearchScope", "system", "integration", "process", "glossaryTerm", "repository", "team");
            case "system" -> List.of("boundedContext", "codeSearchScope", "integration", "process", "system", "repository", "team", "glossaryTerm");
            case "repository" -> List.of("codeSearchScope", "system", "boundedContext", "integration", "process");
            case "codeSearchScope" -> List.of("boundedContext", "system", "process", "integration", "repository");
            case "integration" -> List.of("system", "boundedContext", "codeSearchScope", "process", "repository");
            case "team" -> List.of("system", "boundedContext", "process", "codeSearchScope", "repository");
            case "glossaryTerm" -> List.of("boundedContext", "process", "system", "glossaryTerm", "codeSearchScope");
            default -> List.of("system", "boundedContext", "integration", "codeSearchScope", "process");
        };
        int rank = order.indexOf(type);
        return rank < 0 ? order.size() : rank;
    }

    private static String modelType(String type) {
        return switch (type) {
            case "bounded-context" -> "boundedContext";
            case "code-search-scope" -> "codeSearchScope";
            case "term" -> "glossaryTerm";
            case "handoff-rule" -> "handoffRule";
            default -> type;
        };
    }

    private static String purpose(String type) {
        return switch (type) {
            case "system" -> "Read responsibilities and recognition signals.";
            case "boundedContext" -> "Read domain boundaries, language, rules and resolved ownership.";
            case "process" -> "Read business stages, participants and completion conditions.";
            case "integration" -> "Read participant roles, direction and handoff context.";
            case "repository" -> "Read repository purpose and source identity.";
            case "codeSearchScope" -> "Read repository roles and complete search boundaries.";
            case "team" -> "Read team identity and catalog associations; these do not establish ownership.";
            case "glossaryTerm" -> "Read domain meaning and disambiguation.";
            default -> "Read required evidence and the first handoff action.";
        };
    }

    private static void count(Map<String, Integer> returned, Map<String, Integer> omitted, String section, int actual, int total) {
        returned.put(section, actual);
        if (total > actual) omitted.put(section, total - actual);
    }

    private static Map<String, Object> select(Map<String, Object> source, String... keys) {
        var result = new LinkedHashMap<String, Object>();
        for (var key : keys) if (source.containsKey(key)) result.put(key, source.get(key));
        return result;
    }

    private static Map<String, Object> map(Object value) {
        var result = new LinkedHashMap<String, Object>();
        if (value instanceof Map<?, ?> values) values.forEach((key, item) -> result.put(key.toString(), item));
        return result;
    }

    private static Map<String, Object> compact(Map<String, Object> source, int depth) {
        var result = new LinkedHashMap<String, Object>();
        source.entrySet().stream().limit(12).forEach(entry -> result.put(entry.getKey(), compactValue(entry.getValue(), depth + 1)));
        return result;
    }

    private static Object compactValue(Object value, int depth) {
        if (value instanceof Map<?, ?>) return compact(map(value), depth);
        if (value instanceof List<?> list) return list.stream().limit(ITEMS).map(item -> compactValue(item, depth + 1)).toList();
        return value;
    }

    private static int leafCount(Object value) {
        if (value instanceof Map<?, ?> map) return map.values().stream().mapToInt(OperationalContextToolDetailProjection::leafCount).sum();
        if (value instanceof List<?> list) return list.stream().mapToInt(OperationalContextToolDetailProjection::leafCount).sum();
        return value == null ? 0 : 1;
    }

    private record RelationKey(String type, String direction, boolean indirect, List<OpctxNavigationRole> roles) {}
}
