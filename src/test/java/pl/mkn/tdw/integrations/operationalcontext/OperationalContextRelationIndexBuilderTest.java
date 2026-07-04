package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextRelationIndexBuilderTest {

    private final OperationalContextRelationIndexBuilder builder = new OperationalContextRelationIndexBuilder();

    @Test
    void shouldBuildIncomingOutgoingAndNeighborIndexesFromCurrentCatalogShape() {
        var index = builder.build(sampleCatalog());

        var systemRelations = index.entityRelations("system", "crm-customer-service");

        assertTrue(systemRelations.incomingRelations().stream()
                .anyMatch(relation -> relation.relationType().equals("target-system")
                        && relation.source().type().equals("integration")
                        && relation.source().id().equals("crm-customer-to-notification-sync")));
        assertTrue(systemRelations.incomingRelations().stream()
                .anyMatch(relation -> relation.relationType().equals("primary-system")
                        && relation.source().type().equals("process")
                        && relation.source().id().equals("customer-support-process")));
        assertTrue(systemRelations.incomingRelations().stream()
                .anyMatch(relation -> relation.relationType().equals("targets-system")
                        && relation.source().type().equals("code-search-scope")
                        && relation.source().id().equals("crm-customer-service-scope")));
        assertTrue(systemRelations.neighbors().stream().anyMatch(neighbor -> neighbor.id().equals("crm-customer-to-notification-sync")));
    }

    @Test
    void shouldExposeCodeSearchScopeRelationsToRepositories() {
        var index = builder.build(sampleCatalog());

        var scopeRelations = index.entityRelations("code-search-scope", "crm-customer-service-scope");

        assertTrue(scopeRelations.outgoingRelations().stream()
                .anyMatch(relation -> relation.relationType().equals("references-repository")
                        && relation.target().id().equals("crm-customer-service-repo")
                        && relation.role().equals("primary")));
    }

    @Test
    void shouldMergeExactDuplicateRelationsAndKeepProvenance() {
        var index = builder.build(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service",
                        "references", map("repositories", List.of("crm-customer-service-repo", "crm-customer-service-repo"))
                )),
                List.of(),
                List.of(map("id", "crm-customer-service-repo")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        var relations = index.entityRelations("system", "crm-customer-service").outgoingRelations().stream()
                .filter(relation -> relation.relationType().equals("references-repository"))
                .toList();

        assertEquals(1, relations.size());
        assertEquals(1, relations.get(0).provenance().sourceRefs().size());
        assertFalse(relations.get(0).provenance().warnings().isEmpty());
    }

    @Test
    void shouldReportSelfReferencesAndUnknownTargets() {
        var index = builder.build(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service",
                        "references", map(
                                "systems", List.of("crm-customer-service", "missing-system"),
                                "terms", List.of("free-text-term")
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        assertTrue(index.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("SELF_REFERENCE")
                        && finding.severity().equals("error")));
        assertTrue(index.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("UNKNOWN_RELATION_TARGET")
                        && finding.severity().equals("error")
                        && finding.message().contains("missing-system")));
        assertFalse(index.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("UNKNOWN_RELATION_TARGET")
                        && finding.message().contains("free-text-term")));
    }

    @Test
    void shouldLinkOnlyKnownReferenceTermsAndTreatUnknownTermsAsHints() {
        var index = builder.build(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service",
                        "references", map("terms", List.of("known-term", "free-text-term"))
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new OperationalContextDtos.OperationalContextGlossaryTerm(
                        "known-term",
                        "Known term",
                        "business-term",
                        "Known term.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )),
                List.of(),
                List.of(),
                "index"
        ));

        var relations = index.entityRelations("system", "crm-customer-service").outgoingRelations();

        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("references-term")
                        && relation.target().type().equals("term")
                        && relation.target().id().equals("known-term")));
        assertFalse(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("references-term")
                        && relation.target().id().equals("free-text-term")));
        assertFalse(index.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("UNKNOWN_RELATION_TARGET")
                        && finding.message().contains("free-text-term")));
    }

    @Test
    void shouldTreatGlossaryTypedReferencesAsRelationsAndFreeTextAsHints() {
        var index = builder.build(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map("id", "customer-support-process")),
                List.of(map("id", "crm-customer-service")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new OperationalContextDtos.OperationalContextGlossaryTerm(
                                "customer-profile-term",
                                "Customer profile term",
                                "business-term",
                                "Customer profile term.",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("system:crm-customer-service", "process:customer-support-process", "related-term", "Business label", "None"),
                                List.of(),
                                List.of()
                        ),
                        new OperationalContextDtos.OperationalContextGlossaryTerm(
                                "related-term",
                                "Related term",
                                "business-term",
                                "Related term.",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        )
                ),
                List.of(),
                List.of(),
                "index"
        ));

        var relations = index.entityRelations("term", "customer-profile-term").outgoingRelations();

        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("canonical-reference")
                        && relation.target().type().equals("system")
                        && relation.target().id().equals("crm-customer-service")));
        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("canonical-reference")
                        && relation.target().type().equals("process")
                        && relation.target().id().equals("customer-support-process")));
        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("canonical-reference")
                        && relation.target().type().equals("term")
                        && relation.target().id().equals("related-term")));
        assertFalse(index.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("UNKNOWN_RELATION_TARGET")
                        && finding.message().contains("Business label")));
    }

    @Test
    void shouldUseHandoffOperationalContextLinksAsTypedReferences() {
        var index = builder.build(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map("id", "customer-support-process")),
                List.of(map("id", "crm-customer-service")),
                List.of(map("id", "crm-customer-to-notification-sync")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new OperationalContextDtos.OperationalContextHandoffRule(
                        "notification-sync-failure",
                        "Notification sync failure",
                        "Owner of backend",
                        List.of("Backend failed"),
                        List.of(),
                        List.of("correlationId"),
                        List.of("Check backend"),
                        List.of("CRM Customer maintainers"),
                        new OperationalContextDtos.OperationalContextReferences(
                                List.of("crm-customer-service"),
                                List.of(),
                                List.of("customer-support-process"),
                                List.of(),
                                List.of("crm-customer-to-notification-sync"),
                                List.of(),
                                List.of(),
                                List.of()
                        ),
                        List.of()
                )),
                List.of(),
                "index"
        ));

        var relations = index.entityRelations("handoff-rule", "notification-sync-failure").outgoingRelations();

        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("references-system")
                        && relation.target().id().equals("crm-customer-service")));
        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("references-process")
                        && relation.target().id().equals("customer-support-process")));
        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("references-integration")
                        && relation.target().id().equals("crm-customer-to-notification-sync")));
        assertFalse(relations.stream()
                .anyMatch(relation -> relation.target().type().equals("team")
                        && (relation.target().id().equals("Owner of backend")
                        || relation.target().id().equals("CRM Customer maintainers"))));
        assertFalse(index.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("UNKNOWN_RELATION_TARGET")
                        && (finding.message().contains("Owner of backend")
                        || finding.message().contains("CRM Customer maintainers"))));
    }

    private static OperationalContextDtos.OperationalContextCatalog sampleCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(map(
                        "id", "team-a",
                        "name", "Team A",
                        "responsibilities", List.of(map(
                                "targetType", "system",
                                "targetId", "crm-customer-service",
                                "role", "maintainer",
                                "confidence", "high"
                        ))
                )),
                List.of(map(
                        "id", "customer-support-process",
                        "name", "Customer Support Process",
                        "participants", map(
                                "primarySystems", List.of("crm-customer-service"),
                                "supportingSystems", List.of("crm-support-service")
                        ),
                        "references", map(
                                "boundedContexts", List.of("customer-profile-context")
                        ),
                        "steps", List.of(map(
                                "id", "receive-request",
                                "name", "Receive request",
                                "type", "http-request",
                                "references", map("systems", List.of("crm-customer-service"))
                        ))
                )),
                List.of(
                        map(
                                "id", "crm-customer-service",
                                "name", "CRM Customer Service",
                                "references", map("repositories", List.of("crm-customer-service-repo"))
                        ),
                        map("id", "crm-support-service", "name", "Support Service"),
                        map("id", "notification-provider", "name", "Notification Provider")
                ),
                List.of(map(
                        "id", "crm-customer-to-notification-sync",
                        "name", "Customer Notification Sync",
                        "participants", map(
                                "source", map(
                                        "system", "notification-provider",
                                        "role", "producer",
                                        "repositories", List.of("notification-provider-repo")
                                ),
                                "targets", List.of(map(
                                        "system", "crm-customer-service",
                                        "boundedContext", "customer-profile-context",
                                        "repositories", List.of("crm-customer-service-repo"),
                                        "role", "consumer"
                                ))
                        ),
                        "references", map(
                                "processes", List.of("customer-support-process")
                        )
                )),
                List.of(map(
                        "id", "crm-customer-service-repo",
                        "name", "CRM Customer Service Repository",
                        "references", map("systems", List.of("crm-customer-service"))
                ), map("id", "notification-provider-repo", "name", "Notification Provider Repository")),
                List.of(map(
                        "id", "crm-customer-service-scope",
                        "name", "CRM Customer Service Scope",
                        "target", map("type", "system", "id", "crm-customer-service"),
                        "repositories", List.of(map(
                                "repoId", "crm-customer-service-repo",
                                "role", "primary",
                                "priority", 1
                        ))
                )),
                List.of(map(
                        "id", "customer-profile-context",
                        "name", "Customer Profile Context",
                        "references", map("systems", List.of("crm-customer-service")),
                        "relations", List.of(map(
                                "type", "downstream",
                                "targetContextId", "support-context"
                        ))
                ), map("id", "support-context", "name", "Support Context")),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static Map<String, Object> map(Object... keyValues) {
        var map = new LinkedHashMap<String, Object>();
        for (var i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
