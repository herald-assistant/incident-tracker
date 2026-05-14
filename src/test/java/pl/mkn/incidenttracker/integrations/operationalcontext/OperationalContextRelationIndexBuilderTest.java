package pl.mkn.incidenttracker.integrations.operationalcontext;

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

        var systemRelations = index.entityRelations("system", "app-core");

        assertTrue(systemRelations.incomingRelations().stream()
                .anyMatch(relation -> relation.relationType().equals("target-system")
                        && relation.source().type().equals("integration")
                        && relation.source().id().equals("partner-sync")));
        assertTrue(systemRelations.incomingRelations().stream()
                .anyMatch(relation -> relation.relationType().equals("primary-system")
                        && relation.source().type().equals("process")
                        && relation.source().id().equals("core-process")));
        assertTrue(systemRelations.incomingRelations().stream()
                .anyMatch(relation -> relation.relationType().equals("targets-system")
                        && relation.source().type().equals("code-search-scope")
                        && relation.source().id().equals("app-core-scope")));
        assertTrue(systemRelations.neighbors().stream().anyMatch(neighbor -> neighbor.id().equals("partner-sync")));
    }

    @Test
    void shouldExposeCodeSearchScopeRelationsToRepositoriesAndModules() {
        var index = builder.build(sampleCatalog());

        var scopeRelations = index.entityRelations("code-search-scope", "app-core-scope");

        assertTrue(scopeRelations.outgoingRelations().stream()
                .anyMatch(relation -> relation.relationType().equals("includes-repository")
                        && relation.target().id().equals("app-repo")
                        && relation.role().equals("primary")));
        assertTrue(scopeRelations.outgoingRelations().stream()
                .anyMatch(relation -> relation.relationType().equals("includes-module")
                        && relation.target().id().equals("app-module")));
    }

    @Test
    void shouldMergeExactDuplicateRelationsAndKeepProvenance() {
        var index = builder.build(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "app-core",
                        "references", map("repositories", List.of("app-repo", "app-repo"))
                )),
                List.of(),
                List.of(map("id", "app-repo")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        ));

        var relations = index.entityRelations("system", "app-core").outgoingRelations().stream()
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
                        "id", "app-core",
                        "references", map("systems", List.of("app-core", "missing-system"))
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
    }

    @Test
    void shouldTreatGlossaryTypedReferencesAsRelationsAndFreeTextAsHints() {
        var index = builder.build(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map("id", "core-process")),
                List.of(map("id", "app-core")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new OperationalContextDtos.OperationalContextGlossaryTerm(
                                "core-term",
                                "Core term",
                                "business-term",
                                "Core term.",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("system:app-core", "process:core-process", "related-term", "Business label", "None"),
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

        var relations = index.entityRelations("term", "core-term").outgoingRelations();

        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("canonical-reference")
                        && relation.target().type().equals("system")
                        && relation.target().id().equals("app-core")));
        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("canonical-reference")
                        && relation.target().type().equals("process")
                        && relation.target().id().equals("core-process")));
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
                List.of(map("id", "core-process")),
                List.of(map("id", "app-core")),
                List.of(map("id", "core-sync")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new OperationalContextDtos.OperationalContextHandoffRule(
                        "core-failure",
                        "Core failure",
                        "Owner of backend",
                        List.of("Backend failed"),
                        List.of(),
                        List.of("correlationId"),
                        List.of("Check backend"),
                        List.of("CLP Backend maintainers"),
                        new OperationalContextDtos.OperationalContextReferences(
                                List.of("app-core"),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of("core-process"),
                                List.of(),
                                List.of("core-sync"),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        ),
                        List.of()
                )),
                List.of(),
                "index"
        ));

        var relations = index.entityRelations("handoff-rule", "core-failure").outgoingRelations();

        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("references-system")
                        && relation.target().id().equals("app-core")));
        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("references-process")
                        && relation.target().id().equals("core-process")));
        assertTrue(relations.stream()
                .anyMatch(relation -> relation.relationType().equals("references-integration")
                        && relation.target().id().equals("core-sync")));
        assertFalse(relations.stream()
                .anyMatch(relation -> relation.target().type().equals("team")
                        && (relation.target().id().equals("Owner of backend")
                        || relation.target().id().equals("CLP Backend maintainers"))));
        assertFalse(index.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("UNKNOWN_RELATION_TARGET")
                        && (finding.message().contains("Owner of backend")
                        || finding.message().contains("CLP Backend maintainers"))));
    }

    private static OperationalContextDtos.OperationalContextCatalog sampleCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(map(
                        "id", "team-a",
                        "name", "Team A",
                        "responsibilities", List.of(map(
                                "targetType", "system",
                                "targetId", "app-core",
                                "role", "maintainer",
                                "confidence", "high"
                        ))
                )),
                List.of(map(
                        "id", "core-process",
                        "name", "Core Process",
                        "participants", map(
                                "primarySystems", List.of("app-core"),
                                "supportingSystems", List.of("support-service")
                        ),
                        "references", map(
                                "boundedContexts", List.of("core-context")
                        ),
                        "steps", List.of(map(
                                "id", "receive-request",
                                "name", "Receive request",
                                "type", "http-request",
                                "references", map("systems", List.of("app-core"))
                        ))
                )),
                List.of(
                        map(
                                "id", "app-core",
                                "name", "App Core",
                                "references", map("repositories", List.of("app-repo")),
                                "deployment", map(
                                        "serviceNames", List.of("app-core-service")
                                )
                        ),
                        map("id", "support-service", "name", "Support Service"),
                        map("id", "partner-system", "name", "Partner System")
                ),
                List.of(map(
                        "id", "partner-sync",
                        "name", "Partner Sync",
                        "participants", map(
                                "source", map(
                                        "system", "partner-system",
                                        "role", "producer",
                                        "repositories", List.of("partner-repo")
                                ),
                                "targets", List.of(map(
                                        "system", "app-core",
                                        "boundedContext", "core-context",
                                        "repositories", List.of("app-repo"),
                                        "modules", List.of("app-module"),
                                        "role", "consumer"
                                ))
                        ),
                        "references", map(
                                "processes", List.of("core-process")
                        )
                )),
                List.of(map(
                        "id", "app-repo",
                        "name", "App Repository",
                        "references", map("systems", List.of("app-core")),
                        "modules", List.of(map(
                                "id", "app-module",
                                "name", "App Module",
                                "references", map("boundedContexts", List.of("core-context"))
                        ))
                ), map("id", "partner-repo", "name", "Partner Repository")),
                List.of(map(
                        "id", "app-core-scope",
                        "name", "App Core Scope",
                        "target", map(
                                "systems", List.of("app-core"),
                                "processes", List.of("core-process"),
                                "boundedContexts", List.of("core-context")
                        ),
                        "repositories", List.of(map(
                                "repoId", "app-repo",
                                "role", "primary",
                                "priority", 1,
                                "include", true,
                                "moduleIds", List.of("app-module")
                        ))
                )),
                List.of(map(
                        "id", "core-context",
                        "name", "Core Context",
                        "references", map("systems", List.of("app-core")),
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
