package pl.mkn.tdw.integrations.operationalcontext.contract;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class OperationalContextGapContract {

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> FIELDS = Set.of(
            "id", "type", "summary", "description", "severity", "status",
            "suggestedNextSources", "entityType", "entityId"
    );

    private OperationalContextGapContract() {
    }

    public static List<Violation> validateGap(Object value, boolean entityScoped) {
        var errors = new ArrayList<Violation>();
        if (!(value instanceof Map<?, ?> gap)) {
            return List.of(error("", "Gap must be an object"));
        }
        for (var key : gap.keySet()) {
            if (!FIELDS.contains(String.valueOf(key))) {
                errors.add(error("/" + escape(String.valueOf(key)), "Unsupported gap field; use the canonical gap contract"));
            }
        }
        if (!(gap.get("id") instanceof String id) || !ID.matcher(id).matches()) {
            errors.add(error("/id", "Gap requires a stable lowercase kebab-case ID unique across the catalog"));
        }
        if (!(gap.get("summary") instanceof String summary) || !StringUtils.hasText(summary)
                || Set.of("none", "n/a", "todo", "-").contains(summary.trim().toLowerCase(java.util.Locale.ROOT))) {
            errors.add(error("/summary", "Gap requires an actionable summary"));
        }
        optionalEnum(gap, "severity", Set.of("error", "warning", "info"), errors);
        optionalEnum(gap, "status", Set.of("open", "resolved"), errors);
        for (var field : List.of("type", "description")) {
            if (gap.get(field) != null && !(gap.get(field) instanceof String)) {
                errors.add(error("/" + field, "Value must be text"));
            }
        }
        if (gap.containsKey("entityType") || gap.containsKey("entityId")) {
            if (entityScoped) {
                errors.add(error("/entityId", "Entity gaps inherit their parent; explicit targets belong to document gaps"));
            } else {
                if (!(gap.get("entityType") instanceof String type)
                        || java.util.Arrays.stream(OperationalContextCatalogEntityType.values())
                        .noneMatch(candidate -> candidate.externalName().equals(type))) {
                    errors.add(error("/entityType", "Explicit target requires a canonical entity type"));
                }
                if (!(gap.get("entityId") instanceof String id) || !ID.matcher(id).matches()) {
                    errors.add(error("/entityId", "Explicit target requires an entity ID"));
                }
            }
        }
        if (gap.get("suggestedNextSources") != null) {
            if (!(gap.get("suggestedNextSources") instanceof List<?> sources)) {
                errors.add(error("/suggestedNextSources", "Sources must be a list of text"));
            } else {
                for (int index = 0; index < sources.size(); index++) {
                    if (!(sources.get(index) instanceof String source) || !StringUtils.hasText(source)) {
                        errors.add(error("/suggestedNextSources/" + index, "Source must be non-blank text"));
                    }
                }
            }
        }
        return List.copyOf(errors);
    }

    public static List<OperationalContextCatalogFieldError> validateList(Object value, String path) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> gaps)) {
            return List.of(new OperationalContextCatalogFieldError(path, "Gaps must be a list"));
        }
        var errors = new ArrayList<OperationalContextCatalogFieldError>();
        var ids = new java.util.HashSet<String>();
        for (int index = 0; index < gaps.size(); index++) {
            var item = gaps.get(index);
            var prefix = path + "/" + index;
            for (var error : validateGap(item, true)) {
                errors.add(new OperationalContextCatalogFieldError(prefix + error.pointer(), error.message()));
            }
            if (item instanceof Map<?, ?> gap && gap.get("id") instanceof String id && !ids.add(id)) {
                errors.add(new OperationalContextCatalogFieldError(prefix + "/id", "Gap ID must be unique across the catalog"));
            }
        }
        return List.copyOf(errors);
    }

    public static List<OperationalContextCatalogFieldError> unresolvedSignalErrors(Object value, String path) {
        var errors = new ArrayList<OperationalContextCatalogFieldError>();
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                var key = String.valueOf(entry.getKey());
                var child = path + "/" + escape(key);
                if (key.equals("unresolvedGlossaryReferences")) {
                    errors.add(new OperationalContextCatalogFieldError(child,
                            "Unresolved glossary signals are not a question registry; use gaps and remove this field"));
                } else {
                    errors.addAll(unresolvedSignalErrors(entry.getValue(), child));
                }
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                errors.addAll(unresolvedSignalErrors(list.get(index), path + "/" + index));
            }
        }
        return List.copyOf(errors);
    }

    private static void optionalEnum(
            Map<?, ?> gap, String field, Set<String> allowed, List<Violation> errors
    ) {
        if (gap.get(field) != null && !allowed.contains(gap.get(field))) {
            errors.add(error("/" + field, "Value must be one of: " + String.join(", ", allowed.stream().sorted().toList())));
        }
    }

    private static Violation error(String path, String message) {
        return new Violation(path, message);
    }

    public record Violation(String pointer, String message) {
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
