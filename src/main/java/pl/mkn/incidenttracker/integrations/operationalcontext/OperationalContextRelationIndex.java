package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record OperationalContextRelationIndex(
        Map<EntityKey, EntityRef> entities,
        List<ReadModelRelation> relations,
        Map<EntityKey, List<ReadModelRelation>> outgoingRelations,
        Map<EntityKey, List<ReadModelRelation>> incomingRelations,
        Map<EntityKey, List<EntityRef>> neighbors,
        List<ValidationFinding> validationFindings
) {

    public OperationalContextRelationIndex {
        entities = copyMap(entities);
        relations = copyList(relations);
        outgoingRelations = copyListMap(outgoingRelations);
        incomingRelations = copyListMap(incomingRelations);
        neighbors = copyListMap(neighbors);
        validationFindings = copyList(validationFindings);
    }

    public EntityRelations entityRelations(String type, String id) {
        var key = new EntityKey(type, id);
        return new EntityRelations(
                entities.getOrDefault(key, EntityRef.fromKey(key)),
                outgoingRelations.getOrDefault(key, List.of()),
                incomingRelations.getOrDefault(key, List.of()),
                neighbors.getOrDefault(key, List.of())
        );
    }

    public record EntityRelations(
            EntityRef entity,
            List<ReadModelRelation> outgoingRelations,
            List<ReadModelRelation> incomingRelations,
            List<EntityRef> neighbors
    ) {

        public EntityRelations {
            outgoingRelations = copyList(outgoingRelations);
            incomingRelations = copyList(incomingRelations);
            neighbors = copyList(neighbors);
        }
    }

    public record EntityKey(String type, String id) {

        public EntityKey {
            type = normalizeRequired(type, "type");
            id = normalizeRequired(id, "id");
        }

        public String value() {
            return type + ":" + id;
        }
    }

    public record EntityRef(
            String type,
            String id,
            String label,
            String lifecycleStatus,
            String summary
    ) {

        public EntityRef {
            type = normalizeRequired(type, "type");
            id = normalizeRequired(id, "id");
            label = normalizeOptional(label);
            lifecycleStatus = normalizeOptional(lifecycleStatus);
            summary = normalizeOptional(summary);
        }

        public EntityKey key() {
            return new EntityKey(type, id);
        }

        public static EntityRef fromKey(EntityKey key) {
            return new EntityRef(key.type(), key.id(), key.id(), null, null);
        }
    }

    public record SourceRef(
            String file,
            String entityType,
            String entityId,
            String fieldPath,
            String relationRole
    ) {

        public SourceRef {
            file = normalizeRequired(file, "file");
            entityType = normalizeRequired(entityType, "entityType");
            entityId = normalizeRequired(entityId, "entityId");
            fieldPath = normalizeRequired(fieldPath, "fieldPath");
            relationRole = normalizeOptional(relationRole);
        }
    }

    public record Provenance(
            boolean canonical,
            String derivation,
            String confidence,
            List<SourceRef> sourceRefs,
            List<String> warnings
    ) {

        public Provenance {
            derivation = normalizeRequired(derivation, "derivation");
            confidence = normalizeRequired(confidence, "confidence");
            sourceRefs = copyList(sourceRefs);
            warnings = copyTextList(warnings);
        }
    }

    public record ReadModelRelation(
            String relationType,
            String direction,
            EntityKey source,
            EntityKey target,
            String role,
            EntityKey canonicalOwner,
            boolean derived,
            Provenance provenance
    ) {

        public ReadModelRelation {
            relationType = normalizeRequired(relationType, "relationType");
            direction = normalizeRequired(direction, "direction");
            role = normalizeOptional(role);
            canonicalOwner = canonicalOwner != null ? canonicalOwner : source;
            provenance = provenance != null
                    ? provenance
                    : new Provenance(false, "unknown", "unknown", List.of(), List.of());
        }
    }

    public record ValidationFinding(
            String severity,
            String code,
            String message,
            List<SourceRef> sourceRefs
    ) {

        public ValidationFinding {
            severity = normalizeRequired(severity, "severity");
            code = normalizeRequired(code, "code");
            message = normalizeRequired(message, "message");
            sourceRefs = copyList(sourceRefs);
        }
    }

    static <T> List<T> copyList(List<T> values) {
        return values != null ? List.copyOf(values) : List.of();
    }

    static List<String> copyTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var value : values) {
            var normalized = normalizeOptional(value);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    static <K, V> Map<K, V> copyMap(Map<K, V> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    static <K, V> Map<K, List<V>> copyListMap(Map<K, List<V>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<K, List<V>>();
        values.forEach((key, currentValues) -> result.put(key, copyList(currentValues)));
        return Collections.unmodifiableMap(result);
    }

    static <T> List<T> distinct(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(values));
    }

    static Set<EntityKey> entityKeySet(List<ReadModelRelation> relations) {
        var keys = new LinkedHashSet<EntityKey>();
        for (var relation : relations) {
            keys.add(relation.source());
            keys.add(relation.target());
        }
        return Set.copyOf(keys);
    }

    private static String normalizeRequired(String value, String fieldName) {
        var normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
