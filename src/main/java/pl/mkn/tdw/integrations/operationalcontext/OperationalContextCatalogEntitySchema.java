package pl.mkn.tdw.integrations.operationalcontext;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class OperationalContextCatalogEntitySchema {

    private static final Set<String> COMMON = Set.of(
            "id", "name", "shortName", "lifecycleStatus", "summary", "purpose", "aliases", "useFor"
    );

    private static final Map<OperationalContextCatalogEntityType, Set<String>> EDITABLE = editableFields();
    private static final Map<OperationalContextCatalogEntityType, Set<String>> PRESERVE_ONLY = preserveOnlyFields();

    private OperationalContextCatalogEntitySchema() {
    }

    static boolean editable(OperationalContextCatalogEntityType type, String field) {
        return EDITABLE.get(type).contains(field);
    }

    static boolean preserveOnly(OperationalContextCatalogEntityType type, String field) {
        return PRESERVE_ONLY.get(type).contains(field);
    }

    private static Map<OperationalContextCatalogEntityType, Set<String>> editableFields() {
        var result = new EnumMap<OperationalContextCatalogEntityType, Set<String>>(OperationalContextCatalogEntityType.class);
        result.put(OperationalContextCatalogEntityType.SYSTEM, fields(
                "systemType", "systemSubtype", "operationalStatus", "criticality", "participants", "notes", "runtime",
                "sourceCoverage", "gaps", "ownership", "references", "matchSignals", "relations"
        ));
        result.put(OperationalContextCatalogEntityType.REPOSITORY, fields(
                "repositoryType", "criticality", "relations", "evidence", "gaps", "sourceCoverage", "llmToolHints",
                "matchSignals", "git", "references"
        ));
        result.put(OperationalContextCatalogEntityType.CODE_SEARCH_SCOPE, Set.of(
                "id", "name", "scopeType", "lifecycleStatus", "summary", "useFor", "limitations", "target", "repositories"
        ));
        result.put(OperationalContextCatalogEntityType.PROCESS, fields(
                "type", "criticality", "participants", "processBoundary", "lifecycle",
                "completionSignals", "steps", "relations", "failureModes", "dataAndArtifacts", "references", "matchSignals"
        ));
        result.put(OperationalContextCatalogEntityType.INTEGRATION, fields(
                "category", "integrationStyle", "flowDirection", "criticality", "matchSignals",
                "relations", "failureModes", "participants", "references"
        ));
        result.put(OperationalContextCatalogEntityType.BOUNDED_CONTEXT, fields(
                "type", "localLanguageSummary", "ownership", "references", "scope", "semanticBoundary", "relations",
                "evidence", "sourceCoverage", "gaps", "matchSignals", "llmToolHints"
        ));
        result.put(OperationalContextCatalogEntityType.TEAM, fields("type", "matchSignals"));
        result.put(OperationalContextCatalogEntityType.GLOSSARY_TERM, Set.of(
                "id", "term", "category", "lifecycleStatus", "definition", "localMeaningAndBoundaries",
                "aliases", "useFor", "matchSignals", "canonicalReferences", "relatedTerms",
                "doNotConfuseWith", "responsibilityHints", "llmToolHints", "notes"
        ));
        result.put(OperationalContextCatalogEntityType.HANDOFF_RULE, Set.of(
                "id", "title", "useWhen", "doNotUseWhen", "requiredEvidence", "expectedFirstAction",
                "references", "notes", "llmToolHints", "limitations"
        ));
        return Map.copyOf(result);
    }

    private static Map<OperationalContextCatalogEntityType, Set<String>> preserveOnlyFields() {
        var result = new EnumMap<OperationalContextCatalogEntityType, Set<String>>(OperationalContextCatalogEntityType.class);
        result.put(OperationalContextCatalogEntityType.SYSTEM, Set.of("dependencies"));
        result.put(OperationalContextCatalogEntityType.REPOSITORY, Set.of("lookupHints", "persistenceHints", "scannedSubtrees"));
        result.put(OperationalContextCatalogEntityType.CODE_SEARCH_SCOPE, Set.of());
        result.put(OperationalContextCatalogEntityType.PROCESS, Set.of("observability", "outcomes"));
        result.put(OperationalContextCatalogEntityType.INTEGRATION, Set.of());
        result.put(OperationalContextCatalogEntityType.BOUNDED_CONTEXT, Set.of());
        result.put(OperationalContextCatalogEntityType.TEAM, Set.of("references", "relations"));
        result.put(OperationalContextCatalogEntityType.GLOSSARY_TERM, Set.of());
        result.put(OperationalContextCatalogEntityType.HANDOFF_RULE, Set.of());
        return Map.copyOf(result);
    }

    private static Set<String> fields(String... additional) {
        var result = new LinkedHashSet<>(COMMON);
        result.addAll(Set.of(additional));
        return Set.copyOf(result);
    }
}
