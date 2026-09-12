package pl.mkn.tdw.integrations.operationalcontext;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** The writable nested vocabulary of the catalog. Signal names are intentionally open. */
final class OperationalContextCatalogNestedFields {

    private static final Shape REFERENCES = object(
            "systems", "repositories", "processes", "boundedContexts", "integrations", "terms", "teams", "handoffRules"
    );
    private static final Shape OWNERSHIP = object(
            "ownerTeamIds", "ownerLabel", "ownershipStatus", "confidence", "source", "notes"
    );
    private static final Shape RELATIONS = array(object(
            "type", "targetType", "target", "targetContextId", "targetProcessId", "externalSystem", "via", "evidence"
    ));
    private static final Shape SOURCE_COVERAGE = object(
            "status", "scannedSources", "sources", "expectedSources", "limitations"
    );
    private static final Shape GAPS = array(object(
            "id", "type", "summary", "question", "description", "impact", "severity", "status", "suggestedNextSources"
    ));
    private static final Shape SIGNALS = new Shape(Set.of(), Map.of(), null, true, false);
    private static final Shape EVIDENCE = array(object("sourceRef", "evidenceType", "note"));
    private static final Shape PARTICIPANT = object("system", "boundedContext", "repositories", "role", "externalOwner", "notes");
    private static final Shape PROCESS_STEP = object(Map.of(
            "references", REFERENCES,
            "participants", object("systems", "boundedContexts", "integrations"),
            "matchSignals", SIGNALS
    ), "id", "name", "type", "summary", "match");
    private static final Shape LIFECYCLE = object(Map.of(
            "triggers", array(object("type", "name", "exchange")),
            "transitions", array(object("from", "to", "trigger"))
    ), "entryCriteria", "statuses", "terminalStates", "successOutcomes", "partialOutcomes",
            "failedOutcomes", "cancellationOutcomes", "triggers", "transitions");
    private static final Map<OperationalContextCatalogEntityType, Map<String, Shape>> BY_TYPE = schemas();

    private OperationalContextCatalogNestedFields() {
    }

    static void rejectUnknown(
            OperationalContextCatalogEntityType type,
            Map<String, Object> payload,
            List<OperationalContextCatalogFieldError> errors
    ) {
        for (var entry : BY_TYPE.get(type).entrySet()) {
            inspect(payload.get(entry.getKey()), entry.getValue(), "/payload/" + pointer(entry.getKey()), errors, false);
        }
    }

    static void removeUnknown(OperationalContextCatalogEntityType type, Map<String, Object> payload) {
        for (var entry : BY_TYPE.get(type).entrySet()) {
            inspect(payload.get(entry.getKey()), entry.getValue(), "/payload/" + pointer(entry.getKey()), null, true);
        }
    }

    private static void inspect(
            Object value,
            Shape shape,
            String path,
            List<OperationalContextCatalogFieldError> errors,
            boolean remove
    ) {
        if (shape.items() != null) {
            if (value instanceof Collection<?> collection) {
                var index = 0;
                for (var item : collection) {
                    inspect(item, shape.items(), path + "/" + index++, errors, remove);
                }
            } else if (shape.objectOrArray()) {
                inspectObject(value, shape.items(), path, errors, remove);
            }
            return;
        }
        inspectObject(value, shape, path, errors, remove);
    }

    @SuppressWarnings("unchecked")
    private static void inspectObject(
            Object value,
            Shape shape,
            String path,
            List<OperationalContextCatalogFieldError> errors,
            boolean remove
    ) {
        if (!(value instanceof Map<?, ?> raw)) {
            return;
        }
        var map = (Map<String, Object>) raw;
        if (shape.signals()) {
            var strengths = Set.of("exact", "strong", "medium", "weak");
            if (map.keySet().stream().noneMatch(strengths::contains)) {
                return; // Legacy untiered signal names are part of the contract.
            }
            for (var key : List.copyOf(map.keySet())) {
                if (!strengths.contains(key)) {
                    unknown(map, key, path, errors, remove);
                }
            }
            return; // Every signal name within a strength bucket is intentional.
        }
        for (var key : List.copyOf(map.keySet())) {
            if (!shape.keys().contains(key)) {
                unknown(map, key, path, errors, remove);
            } else if (shape.children().containsKey(key)) {
                inspect(map.get(key), shape.children().get(key), path + "/" + pointer(key), errors, remove);
            }
        }
    }

    private static void unknown(
            Map<String, Object> map,
            String key,
            String path,
            List<OperationalContextCatalogFieldError> errors,
            boolean remove
    ) {
        if (remove) {
            map.remove(key);
        } else {
            errors.add(new OperationalContextCatalogFieldError(path + "/" + pointer(key), "Unknown field is not writable"));
        }
    }

    private static String pointer(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private static Shape object(String... keys) {
        return new Shape(Set.of(keys), Map.of(), null, false, false);
    }

    private static Shape object(Map<String, Shape> children, String... keys) {
        var allowed = new LinkedHashSet<>(Set.of(keys));
        allowed.addAll(children.keySet());
        return new Shape(Set.copyOf(allowed), children, null, false, false);
    }

    private static Shape array(Shape item) {
        return new Shape(Set.of(), Map.of(), item, false, false);
    }

    private static Shape objectOrArray(Shape item) {
        return new Shape(Set.of(), Map.of(), item, false, true);
    }

    private static Map<OperationalContextCatalogEntityType, Map<String, Shape>> schemas() {
        var common = Map.of(
                "references", REFERENCES,
                "ownership", OWNERSHIP,
                "relations", RELATIONS,
                "matchSignals", SIGNALS,
                "sourceCoverage", objectOrArray(SOURCE_COVERAGE),
                "gaps", GAPS
        );
        var result = new EnumMap<OperationalContextCatalogEntityType, Map<String, Shape>>(OperationalContextCatalogEntityType.class);
        for (var type : OperationalContextCatalogEntityType.values()) {
            result.put(type, new java.util.LinkedHashMap<>(common));
        }
        result.get(OperationalContextCatalogEntityType.SYSTEM).putAll(Map.of(
                "participants", object("externalOwner"),
                "runtime", object("configurationDirectory")
        ));
        result.get(OperationalContextCatalogEntityType.REPOSITORY).putAll(Map.of(
                "git", object("provider", "group", "project", "projectPath", "defaultBranch", "url", "aliases", "inferred"),
                "evidence", EVIDENCE,
                "llmToolHints", object("answerWhenUserMentions", "disambiguateFrom")
        ));
        result.get(OperationalContextCatalogEntityType.CODE_SEARCH_SCOPE).putAll(Map.of(
                "target", object("type", "id"),
                "repositories", array(object("repoId", "role", "priority", "reason", "readFor", "searchMode", "pathPrefixes"))
        ));
        result.get(OperationalContextCatalogEntityType.PROCESS).putAll(Map.of(
                "participants", object("actors", "primarySystems", "supportingSystems", "externalSystems", "platformComponents"),
                "processBoundary", object("businessCapability", "startsWhen", "endsWhen", "includes", "excludes", "assumptions"),
                "outcomes", object("successArtifacts"),
                "lifecycle", LIFECYCLE,
                "completionSignals", object("successful", "partial", "failed", "cancelled"),
                "steps", array(PROCESS_STEP),
                "failureModes", array(object("id", "name", "summary", "affectedStep", "signals")),
                "dataAndArtifacts", object("primaryObjects", "inputArtifacts", "outputArtifacts", "persistedEntities", "readModels", "auditArtifacts", "notes")
        ));
        result.get(OperationalContextCatalogEntityType.INTEGRATION).putAll(Map.of(
                "participants", object(Map.of(
                        "source", PARTICIPANT,
                        "targets", array(PARTICIPANT),
                        "intermediaries", array(PARTICIPANT),
                        "finalTargets", array(PARTICIPANT)
                ), "source", "targets", "intermediaries", "finalTargets"),
                "failureModes", array(object("name", "type", "symptom", "impact"))
        ));
        result.get(OperationalContextCatalogEntityType.BOUNDED_CONTEXT).putAll(Map.of(
                "scope", object("includes", "excludes", "businessCapabilities", "coreEntities", "keyDecisions"),
                "semanticBoundary", object("coreConcepts", "localConcepts", "canonicalEntities", "commands", "events", "invariants", "ownsLanguage", "doesNotOwn"),
                "evidence", EVIDENCE,
                "llmToolHints", object("answerWhenUserMentions", "disambiguateFrom", "usefulSearchKeywords", "explanationStyle")
        ));
        return Map.copyOf(result);
    }

    private record Shape(
            Set<String> keys,
            Map<String, Shape> children,
            Shape items,
            boolean signals,
            boolean objectOrArray
    ) {
    }
}
