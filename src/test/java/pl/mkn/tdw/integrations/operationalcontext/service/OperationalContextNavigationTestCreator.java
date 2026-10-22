package pl.mkn.tdw.integrations.operationalcontext.service;

public final class OperationalContextNavigationTestCreator {
    private OperationalContextNavigationTestCreator() {}
    public static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog catalog(boolean mirrored, int contexts) {
        var ids = java.util.stream.IntStream.range(0, contexts).mapToObj(i -> "crm-context-" + i).toList();
        var system = new java.util.LinkedHashMap<String, Object>();
        system.put("id", "crm-contacts");
        system.put("systemType", "internal-service");
        system.put("systemSubtype", "backend");
        system.put("ownership", java.util.Map.of("ownerLabel", "CRM Contacts Team", "ownershipStatus", "explicit", "confidence", "high"));
        if (mirrored) system.put("references", java.util.Map.of("boundedContexts", ids, "repositories", java.util.List.of("crm-contacts-repo")));
        return pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.catalogFromRaw(
                java.util.List.of(), java.util.List.of(), java.util.List.of(system), java.util.List.of(),
                java.util.List.of(java.util.Map.of("id", "crm-contacts-repo", "git", java.util.Map.of("group", "CRM", "project", "contacts", "projectPath", "CRM/contacts"),
                        "references", java.util.Map.of("systems", java.util.List.of("crm-contacts")))),
                java.util.List.of(java.util.Map.of("id", "crm-contacts-code", "target", java.util.Map.of("type", "system", "id", "crm-contacts"),
                        "repositories", java.util.List.of(java.util.Map.of("repoId", "crm-contacts-repo", "role", "primary", "searchMode", "whole-repository")))),
                ids.stream().map(id -> java.util.Map.<String, Object>of("id", id, "references", java.util.Map.of("systems", java.util.List.of("crm-contacts")))).toList(),
                java.util.List.of(), java.util.List.of(), java.util.List.of());
    }

    public static OperationalContextNavigationService navigation() {
        return new OperationalContextNavigationService(new OperationalContextRelationIndexBuilder());
    }
    public static OperationalContextCatalogSearch search() {
        return new OperationalContextCatalogSearch(navigation());
    }
    public static OperationalContextCodeSearchReadModelBuilder codeSearch() {
        return new OperationalContextCodeSearchReadModelBuilder();
    }
}
