package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.Arrays;

public enum OperationalContextCatalogEntityType {
    SYSTEM("system", "systems.yml", "systems"),
    REPOSITORY("repository", "repo-map.yml", "repositories"),
    CODE_SEARCH_SCOPE("code-search-scope", "code-search-scopes.yml", "codeSearchScopes"),
    PROCESS("process", "processes.yml", "processes"),
    INTEGRATION("integration", "integrations.yml", "integrations"),
    BOUNDED_CONTEXT("bounded-context", "bounded-contexts.yml", "boundedContexts"),
    TEAM("team", "teams.yml", "teams"),
    GLOSSARY_TERM("glossary-term", "glossary.yml", "terms"),
    HANDOFF_RULE("handoff-rule", "handoff-rules.yml", "handoffRules");

    private final String externalName;
    private final String logicalDocument;
    private final String collectionName;

    OperationalContextCatalogEntityType(String externalName, String logicalDocument, String collectionName) {
        this.externalName = externalName;
        this.logicalDocument = logicalDocument;
        this.collectionName = collectionName;
    }

    public String externalName() {
        return externalName;
    }

    String logicalDocument() {
        return logicalDocument;
    }

    String collectionName() {
        return collectionName;
    }

    public static OperationalContextCatalogEntityType fromExternalName(String value) {
        if (!StringUtils.hasText(value)) {
            throw OperationalContextCatalogMaintenanceException.invalidType(value);
        }
        var normalized = value.trim().replace('_', '-');
        return Arrays.stream(values())
                .filter(type -> type.externalName.equals(normalized))
                .findFirst()
                .orElseThrow(() -> OperationalContextCatalogMaintenanceException.invalidType(value));
    }
}
