package pl.mkn.incidenttracker.api.operationalcontext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProfiledReadModelDto;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextAdapter;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextProperties;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.ReadModelRelation;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextReadModelValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextReadModelContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepTypicalReadModelPayloadSnapshotForFeLlmAndTools() throws Exception {
        var catalog = typicalCatalog();
        var service = new OperationalContextViewService(port(catalog));

        var entityRelations = service.entityRelationsReadModel("system", "agreement-service");
        var codeSearch = service.codeSearchReadModel("system", "agreement-service");
        var implementation = service.implementationReadModel("process", "agreement-submit-process");
        var flow = service.flowReadModel("process", "agreement-submit-process");
        var blastRadius = service.blastRadiusReadModel("endpoint", "/agreements");

        var snapshot = map(
                "catalogValidation", validationSummary(new OperationalContextReadModelValidator().validate(catalog)),
                "entityRelations", map(
                        "contract", entityRelations.contract(),
                        "fields", fieldNames(entityRelations),
                        "target", ref(entityRelations.analysisTarget()),
                        "incomingRelationCounts", relationCounts(entityRelations.incomingRelations()),
                        "outgoingRelationCounts", relationCounts(entityRelations.outgoingRelations()),
                        "neighborCount", entityRelations.neighbors().size(),
                        "representativeDerivedRelation", relationSnapshot(entityRelations.incomingRelations(), "targets-system"),
                        "validation", validationSummary(entityRelations.validationFindings())
                ),
                "codeSearch", map(
                        "contract", codeSearch.contract(),
                        "fields", fieldNames(codeSearch),
                        "target", ref(codeSearch.analysisTarget()),
                        "scopeIds", codeSearch.scopes().stream().map(scope -> ref(scope.scope())).toList(),
                        "scopeTargets", refs(codeSearch.scopes().get(0).targets()),
                        "repositoryIds", refs(codeSearch.repositories().stream().map(repository -> repository.repository()).toList()),
                        "aggregatedPackages", codeSearch.aggregatedHints().packagePrefixes(),
                        "aggregatedEndpointHints", codeSearch.aggregatedHints().endpointHints(),
                        "aggregatedTables", codeSearch.aggregatedHints().databaseHints().tables(),
                        "limitations", codeSearch.limitations(),
                        "scopeProvenance", provenanceSummary(codeSearch.scopes().get(0).provenance()),
                        "validation", validationSummary(codeSearch.validationFindings())
                ),
                "implementationMap", map(
                        "contract", implementation.contract(),
                        "fields", fieldNames(implementation),
                        "target", ref(implementation.analysisTarget()),
                        "implementationIds", implementation.implementations().stream()
                                .map(view -> view.id())
                                .toList(),
                        "firstImplementation", map(
                                "kind", implementation.implementations().get(0).implementationKind(),
                                "lifecycleRole", implementation.implementations().get(0).lifecycleRole(),
                                "migrationStatus", implementation.implementations().get(0).migrationStatus(),
                                "systems", refs(implementation.implementations().get(0).systems()),
                                "boundedContexts", refs(implementation.implementations().get(0).boundedContexts()),
                                "processes", refs(implementation.implementations().get(0).processes()),
                                "repository", ref(implementation.implementations().get(0).repository()),
                                "module", implementation.implementations().get(0).module().id(),
                                "provenance", provenanceSummary(implementation.implementations().get(0).provenance())
                        ),
                        "limitations", implementation.limitations(),
                        "validation", validationSummary(implementation.validationFindings())
                ),
                "flow", map(
                        "contract", flow.contract(),
                        "fields", fieldNames(flow),
                        "target", ref(flow.analysisTarget()),
                        "trigger", map(
                                "channel", flow.trigger().channel(),
                                "endpoints", flow.trigger().endpoints(),
                                "sources", refs(flow.trigger().sources()),
                                "targets", refs(flow.trigger().targets()),
                                "provenance", provenanceSummary(flow.trigger().provenance())
                        ),
                        "steps", flow.steps().stream()
                                .map(step -> step.id() + ":" + step.kind())
                                .toList(),
                        "edgeCount", flow.edges().size(),
                        "involvedSystems", refs(flow.involvedSystems()),
                        "involvedIntegrations", refs(flow.involvedIntegrations()),
                        "firstStepProvenance", provenanceSummary(flow.steps().get(0).provenance()),
                        "limitations", flow.limitations(),
                        "validation", validationSummary(flow.validationFindings())
                ),
                "blastRadius", map(
                        "contract", blastRadius.contract(),
                        "fields", fieldNames(blastRadius),
                        "target", ref(blastRadius.analysisTarget()),
                        "impactedFlowIds", refs(blastRadius.impactedFlows().stream()
                                .map(flowImpact -> flowImpact.flow())
                                .toList()),
                        "stepImpacts", blastRadius.impactedFlows().get(0).impactedSteps().stream()
                                .map(step -> step.stepId() + ":" + step.impactType())
                                .toList(),
                        "impactedSystems", refs(blastRadius.impactedSystems().stream()
                                .map(system -> system.entity())
                                .toList()),
                        "impactedIntegrations", refs(blastRadius.impactedIntegrations().stream()
                                .map(integration -> integration.entity())
                                .toList()),
                        "suggestedNextEvidence", blastRadius.suggestedNextEvidence(),
                        "flowProvenance", provenanceSummary(blastRadius.impactedFlows().get(0).provenance()),
                        "limitations", blastRadius.limitations(),
                        "validation", validationSummary(blastRadius.validationFindings())
                )
        );

        assertEquals(objectMapper.readTree(expectedSnapshot()), objectMapper.valueToTree(snapshot));
    }

    @Test
    void shouldExposeCompactDefaultProfilesWithoutChangingExpandedPayloads() throws Exception {
        var catalog = typicalCatalog();
        var service = new OperationalContextViewService(port(catalog));

        var expandedEntity = service.entity("system", "agreement-service");
        var compactEntity = (OperationalContextProfiledReadModelDto) service.entity("system", "agreement-service", "default");
        var expandedFlow = service.flowReadModel("process", "agreement-submit-process");
        var compactFlow = (OperationalContextProfiledReadModelDto) service.flowReadModel("process", "agreement-submit-process", "default");
        var compactRelations = (OperationalContextProfiledReadModelDto) service.entityRelationsReadModel("system", "agreement-service", "default");
        var compactCodeSearch = (OperationalContextProfiledReadModelDto) service.codeSearchReadModel("system", "agreement-service", "default");
        var compactBlastRadius = (OperationalContextProfiledReadModelDto) service.blastRadiusReadModel("endpoint", "/agreements", "default");

        assertEquals("default", compactEntity.profile());
        assertEquals("operational-context.entity-detail", compactEntity.contract());
        assertFalse(objectMapper.writeValueAsString(compactEntity).contains("\"rawSourcePreview\":"));
        assertFalse(compactEntity.links().isEmpty());

        assertEquals("default", compactFlow.profile());
        assertTrue(compactFlow.links().stream().anyMatch(link -> link.rel().equals("expanded")));
        assertTrue(compactFlow.availableExpansions().contains("profile=expanded"));
        assertFalse(compactFlow.suggestedNextReads().isEmpty());
        assertTrue(objectMapper.writeValueAsBytes(compactFlow).length < objectMapper.writeValueAsBytes(expandedFlow).length);

        assertEquals("default", compactRelations.profile());
        var incomingRelations = objectList(compactRelations.data().get("incomingRelations"));
        assertFalse(incomingRelations.isEmpty());
        assertScoreOrdering(incomingRelations);
        assertTrue(incomingRelations.get(0).containsKey("relevanceScore"));
        assertTrue(String.valueOf(incomingRelations.get(0).get("reasonToRead")).contains("confidence"));
        assertTrue(String.valueOf(incomingRelations.get(0).get("suggestedNextRead")).contains("/api/operational-context"));
        assertTrue(compactRelations.suggestedNextReads().stream().anyMatch(read -> read.contains("--")));
        var relationProvenance = objectMap(compactRelations.provenance());
        assertTrue(objectMap(relationProvenance.get("sourceRefs")).containsKey("byFile"));
        var firstSourceRef = objectMap(compactRelations.sourceRefs().get(0));
        assertTrue(firstSourceRef.containsKey("refId"));
        assertTrue(firstSourceRef.containsKey("target"));
        assertFalse(firstSourceRef.containsKey("entityType"));
        assertFalse(firstSourceRef.containsKey("entityId"));
        assertFalse(firstSourceRef.containsKey("fieldPath"));

        assertEquals("default", compactCodeSearch.profile());
        assertTrue(compactCodeSearch.suggestedTools().contains("gitlab_search_repository_candidates"));

        assertEquals("default", compactBlastRadius.profile());
        var impactedFlows = objectList(compactBlastRadius.data().get("impactedFlows"));
        assertFalse(impactedFlows.isEmpty());
        assertTrue(impactedFlows.get(0).containsKey("relevanceScore"));
        assertTrue(String.valueOf(impactedFlows.get(0).get("reasonToRead")).contains("impacted steps"));
        var impactedSteps = objectList(impactedFlows.get(0).get("impactedSteps"));
        assertTrue(impactedSteps.get(0).containsKey("reasonToRead"));
        var impactedNodes = objectMap(compactBlastRadius.data().get("impactedNodes"));
        assertScoreOrdering(objectList(impactedNodes.get("systems")));
        assertTrue(compactBlastRadius.suggestedNextReads().stream().anyMatch(read -> read.contains("/flow")));
    }

    @Test
    void shouldKeepNoProfileReadModelsBackwardCompatibleWithExpandedShape() {
        var catalog = typicalCatalog();
        var service = new OperationalContextViewService(port(catalog));

        var noProfile = service.flowReadModel("process", "agreement-submit-process");
        var explicitExpanded = service.flowReadModel("process", "agreement-submit-process", "expanded");

        assertEquals(objectMapper.valueToTree(noProfile), objectMapper.valueToTree(explicitExpanded));
    }

    @Test
    void shouldTruncateBroadRuntimeClassBlastRadiusInDefaultProfile() throws Exception {
        var service = new OperationalContextViewService(new OperationalContextAdapter(new OperationalContextProperties()));

        var expanded = service.blastRadiusReadModel("class", "NewLimitOrderController");
        var compact = (OperationalContextProfiledReadModelDto) service.blastRadiusReadModel(
                "class",
                "NewLimitOrderController",
                "default"
        );

        assertEquals("default", compact.profile());
        assertTrue(compact.truncation().truncated());
        assertTrue(compact.limitations().stream().anyMatch(limitation -> limitation.contains("Broad non-catalog signal")));
        assertTrue(objectMapper.writeValueAsBytes(compact).length < objectMapper.writeValueAsBytes(expanded).length / 5);
    }

    private String expectedSnapshot() {
        return """
                {
                  "catalogValidation": {
                    "info": 0,
                    "warning": 0,
                    "error": 0
                  },
                  "entityRelations": {
                    "contract": "operational-context.entity-relations",
                    "fields": [
                      "contract",
                      "contractVersion",
                      "analysisTarget",
                      "outgoingRelations",
                      "incomingRelations",
                      "neighbors",
                      "validationFindings"
                    ],
                    "target": "system:agreement-service",
                    "incomingRelationCounts": {
                      "primary-system": 1,
                      "references-system": 4,
                      "responsible-for": 1,
                      "source-system": 1,
                      "targets-system": 1
                    },
                    "outgoingRelationCounts": {},
                    "neighborCount": 8,
                    "representativeDerivedRelation": {
                      "edge": "code-search-scope:agreement-service-scope -targets-system-> system:agreement-service",
                      "derived": true,
                      "provenance": {
                        "canonical": false,
                        "derivation": "derived-from-included-repository-reference",
                        "confidence": "medium",
                        "sourceRefCount": 2
                      }
                    },
                    "validation": {
                      "info": 0,
                      "warning": 0,
                      "error": 0
                    }
                  },
                  "codeSearch": {
                    "contract": "operational-context.code-search",
                    "fields": [
                      "contract",
                      "contractVersion",
                      "profile",
                      "analysisTarget",
                      "scopes",
                      "repositories",
                      "aggregatedHints",
                      "limitations",
                      "validationFindings"
                    ],
                    "target": "system:agreement-service",
                    "scopeIds": [
                      "code-search-scope:agreement-service-scope"
                    ],
                    "scopeTargets": [
                      "bounded-context:agreement-process-management",
                      "process:agreement-submit-process",
                      "system:agreement-service",
                      "term:agreement"
                    ],
                    "repositoryIds": [
                      "repository:agreement-service-repo"
                    ],
                    "aggregatedPackages": [
                      "com.example.agreement",
                      "com.example.agreement.api"
                    ],
                    "aggregatedEndpointHints": [
                      "/agreements"
                    ],
                    "aggregatedTables": [
                      "AGREEMENT_PROCESS"
                    ],
                    "limitations": [
                      "Generated clients are outside this compact test catalog."
                    ],
                    "scopeProvenance": {
                      "canonical": true,
                      "derivation": "direct-yaml",
                      "confidence": "high",
                      "sourceRefCount": 1
                    },
                    "validation": {
                      "info": 0,
                      "warning": 0,
                      "error": 0
                    }
                  },
                  "implementationMap": {
                    "contract": "operational-context.implementation-map",
                    "fields": [
                      "contract",
                      "contractVersion",
                      "profile",
                      "analysisTarget",
                      "implementations",
                      "limitations",
                      "validationFindings"
                    ],
                    "target": "process:agreement-submit-process",
                    "implementationIds": [
                      "agreement-service-scope::agreement-service-repo::agreement-api"
                    ],
                    "firstImplementation": {
                      "kind": "implementation",
                      "lifecycleRole": "primary",
                      "migrationStatus": "active",
                      "systems": [
                        "system:agreement-service"
                      ],
                      "boundedContexts": [
                        "bounded-context:agreement-process-management"
                      ],
                      "processes": [
                        "process:agreement-submit-process"
                      ],
                      "repository": "repository:agreement-service-repo",
                      "module": "agreement-api",
                      "provenance": {
                        "canonical": false,
                        "derivation": "derived-from-code-search-read-model",
                        "confidence": "high",
                        "sourceRefCount": 3
                      }
                    },
                    "limitations": [
                      "Generated clients are outside this compact test catalog."
                    ],
                    "validation": {
                      "info": 0,
                      "warning": 0,
                      "error": 0
                    }
                  },
                  "flow": {
                    "contract": "operational-context.flow",
                    "fields": [
                      "contract",
                      "contractVersion",
                      "profile",
                      "analysisTarget",
                      "trigger",
                      "steps",
                      "edges",
                      "involvedSystems",
                      "involvedBoundedContexts",
                      "involvedIntegrations",
                      "involvedDataStores",
                      "limitations",
                      "validationFindings"
                    ],
                    "target": "process:agreement-submit-process",
                    "trigger": {
                      "channel": "http",
                      "endpoints": [
                        "POST /agreements/{agreementId}/submit"
                      ],
                      "sources": [
                        "system:external-system-b",
                        "system:operator-frontend"
                      ],
                      "targets": [
                        "system:agreement-service"
                      ],
                      "provenance": {
                        "canonical": false,
                        "derivation": "derived-from-process-lifecycle-triggers",
                        "confidence": "high",
                        "sourceRefCount": 1
                      }
                    },
                    "steps": [
                      "receive-submit:http-endpoint",
                      "persist-state:database-write",
                      "request-notification:integration-call",
                      "deliver-external:integration-call"
                    ],
                    "edgeCount": 3,
                    "involvedSystems": [
                      "system:agreement-service",
                      "system:external-system-b",
                      "system:notification-service",
                      "system:operator-frontend"
                    ],
                    "involvedIntegrations": [
                      "integration:agreement-to-notifications",
                      "integration:notifications-to-external-b"
                    ],
                    "firstStepProvenance": {
                      "canonical": false,
                      "derivation": "derived-from-process-step-and-implementation-read-model",
                      "confidence": "medium",
                      "sourceRefCount": 1
                    },
                    "limitations": [
                      "Generated clients are outside this compact test catalog."
                    ],
                    "validation": {
                      "info": 0,
                      "warning": 0,
                      "error": 0
                    }
                  },
                  "blastRadius": {
                    "contract": "operational-context.blast-radius",
                    "fields": [
                      "contract",
                      "contractVersion",
                      "profile",
                      "analysisTarget",
                      "impactedFlows",
                      "impactedSystems",
                      "impactedBoundedContexts",
                      "impactedIntegrations",
                      "impactedDataStores",
                      "impactedImplementations",
                      "suggestedNextEvidence",
                      "limitations",
                      "validationFindings"
                    ],
                    "target": "endpoint:/agreements",
                    "impactedFlowIds": [
                      "process:agreement-submit-process"
                    ],
                    "stepImpacts": [
                      "receive-submit:direct-hit",
                      "persist-state:downstream",
                      "request-notification:downstream",
                      "deliver-external:downstream"
                    ],
                    "impactedSystems": [
                      "system:agreement-service",
                      "system:external-system-b",
                      "system:notification-service",
                      "system:operator-frontend"
                    ],
                    "impactedIntegrations": [
                      "integration:agreement-to-notifications",
                      "integration:notifications-to-external-b"
                    ],
                    "suggestedNextEvidence": [
                      "Inspect impacted flow steps before reading broad raw catalog entries.",
                      "Use code-search scopes from impacted implementations to fetch targeted source code.",
                      "For integration symptoms, verify source/target systems and transport-specific failure modes."
                    ],
                    "flowProvenance": {
                      "canonical": false,
                      "derivation": "derived-from-flow-read-model",
                      "confidence": "medium",
                      "sourceRefCount": 1
                    },
                    "limitations": [
                      "Generated clients are outside this compact test catalog."
                    ],
                    "validation": {
                      "info": 0,
                      "warning": 0,
                      "error": 0
                    }
                  }
                }
                """;
    }

    private List<String> fieldNames(Object payload) {
        var fields = new ArrayList<String>();
        objectMapper.valueToTree(payload).fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private Map<String, Integer> relationCounts(List<ReadModelRelation> relations) {
        var sorted = new TreeMap<String, Integer>();
        for (var relation : relations) {
            sorted.merge(relation.relationType(), 1, Integer::sum);
        }
        return new LinkedHashMap<>(sorted);
    }

    private Map<String, Object> relationSnapshot(List<ReadModelRelation> relations, String relationType) {
        return relations.stream()
                .filter(relation -> relation.relationType().equals(relationType))
                .sorted(Comparator
                        .comparing((ReadModelRelation relation) -> key(relation.source()))
                        .thenComparing(relation -> key(relation.target())))
                .findFirst()
                .map(relation -> map(
                        "edge", key(relation.source()) + " -" + relation.relationType() + "-> " + key(relation.target()),
                        "derived", relation.derived(),
                        "provenance", provenanceSummary(relation.provenance())
                ))
                .orElseGet(() -> map("edge", "missing"));
    }

    private Map<String, Object> provenanceSummary(Provenance provenance) {
        return map(
                "canonical", provenance.canonical(),
                "derivation", provenance.derivation(),
                "confidence", provenance.confidence(),
                "sourceRefCount", provenance.sourceRefs().size()
        );
    }

    private Map<String, Integer> validationSummary(List<ValidationFinding> findings) {
        var summary = new LinkedHashMap<String, Integer>();
        summary.put("info", 0);
        summary.put("warning", 0);
        summary.put("error", 0);
        for (var finding : findings) {
            summary.computeIfPresent(finding.severity(), (ignored, count) -> count + 1);
        }
        return summary;
    }

    private List<String> refs(List<EntityRef> refs) {
        return refs.stream()
                .map(this::ref)
                .sorted()
                .toList();
    }

    private String ref(EntityRef ref) {
        return ref.type() + ":" + ref.id();
    }

    private String key(EntityKey key) {
        return key.type() + ":" + key.id();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }

    private void assertScoreOrdering(List<Map<String, Object>> items) {
        var previous = Double.MAX_VALUE;
        for (var item : items) {
            var score = ((Number) item.getOrDefault("relevanceScore", 0.0)).doubleValue();
            assertTrue(score <= previous, "Expected non-increasing relevance scores");
            previous = score;
        }
    }

    private static OperationalContextPort port(OperationalContextCatalog catalog) {
        return query -> catalog;
    }

    private static OperationalContextCatalog typicalCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(map(
                        "id", "team-a",
                        "name", "Agreement Team",
                        "responsibilities", List.of(
                                map("targetType", "system", "targetId", "agreement-service", "role", "owner", "confidence", "high"),
                                map("targetType", "repository", "targetId", "agreement-service-repo", "role", "owner", "confidence", "high"),
                                map("targetType", "process", "targetId", "agreement-submit-process", "role", "owner", "confidence", "high"),
                                map("targetType", "bounded-context", "targetId", "agreement-process-management", "role", "owner", "confidence", "high"),
                                map("targetType", "integration", "targetId", "agreement-to-notifications", "role", "owner", "confidence", "high")
                        )
                )),
                List.of(map(
                        "id", "agreement-submit-process",
                        "name", "Agreement Submit Process",
                        "type", "request-flow",
                        "participants", map(
                                "primarySystems", List.of("agreement-service"),
                                "supportingSystems", List.of("notification-service"),
                                "externalSystems", List.of("operator-frontend", "external-system-b")
                        ),
                        "lifecycle", map(
                                "triggers", List.of(map(
                                        "type", "api",
                                        "endpoint", "POST /agreements/{agreementId}/submit"
                                ))
                        ),
                        "steps", List.of(
                                map(
                                        "id", "receive-submit",
                                        "name", "Receive Submit Request",
                                        "type", "http-endpoint",
                                        "summary", "Frontend submits agreement for processing.",
                                        "references", map(
                                                "systems", List.of("operator-frontend", "agreement-service"),
                                                "boundedContexts", List.of("agreement-process-management")
                                        ),
                                        "match", map(
                                                "endpointPrefixes", List.of("/agreements"),
                                                "classHints", List.of("AgreementSubmitController")
                                        )
                                ),
                                map(
                                        "id", "persist-state",
                                        "name", "Persist Process State",
                                        "type", "database-write",
                                        "summary", "Write process state into agreement database.",
                                        "references", map(
                                                "systems", List.of("agreement-service"),
                                                "boundedContexts", List.of("agreement-process-management")
                                        ),
                                        "match", map(
                                                "classHints", List.of("AgreementProcessRepository"),
                                                "tables", List.of("AGREEMENT_PROCESS")
                                        )
                                ),
                                map(
                                        "id", "request-notification",
                                        "name", "Request Notification",
                                        "summary", "Agreement process asks notifications capability to send a message.",
                                        "references", map(
                                                "systems", List.of("agreement-service", "notification-service"),
                                                "boundedContexts", List.of("agreement-process-management", "notifications"),
                                                "integrations", List.of("agreement-to-notifications")
                                        ),
                                        "match", map("classHints", List.of("NotificationPort"))
                                ),
                                map(
                                        "id", "deliver-external",
                                        "name", "Deliver External Message",
                                        "summary", "Notifications publishes the outbound message to external system B.",
                                        "references", map(
                                                "systems", List.of("notification-service", "external-system-b"),
                                                "boundedContexts", List.of("notifications"),
                                                "integrations", List.of("notifications-to-external-b")
                                        ),
                                        "match", map("topics", List.of("external.notifications"))
                                )
                        )
                )),
                List.of(
                        map("id", "operator-frontend", "name", "Operator Frontend"),
                        map("id", "agreement-service", "name", "Agreement Service"),
                        map("id", "notification-service", "name", "Notification Service"),
                        map("id", "external-system-b", "name", "External System B")
                ),
                List.of(
                        map(
                                "id", "agreement-to-notifications",
                                "name", "Agreement to Notifications",
                                "participants", map(
                                        "source", map("system", "agreement-service", "boundedContext", "agreement-process-management"),
                                        "targets", List.of(map("system", "notification-service", "boundedContext", "notifications"))
                                ),
                                "transport", map(
                                        "protocols", List.of("http"),
                                        "http", map(
                                                "methods", List.of("POST"),
                                                "endpointPrefixes", List.of("/notifications")
                                        )
                                ),
                                "implementation", map("classHints", List.of("NotificationClient"))
                        ),
                        map(
                                "id", "notifications-to-external-b",
                                "name", "Notifications to External B",
                                "participants", map(
                                        "source", map("system", "notification-service", "boundedContext", "notifications"),
                                        "targets", List.of(map("system", "external-system-b"))
                                ),
                                "transport", map(
                                        "protocols", List.of("messaging"),
                                        "messaging", map("topics", List.of("external.notifications"))
                                ),
                                "implementation", map("classHints", List.of("ExternalNotificationPublisher"))
                        )
                ),
                List.of(map(
                        "id", "agreement-service-repo",
                        "name", "Agreement Service Repository",
                        "lifecycleStatus", "active",
                        "git", map(
                                "provider", "gitlab",
                                "group", "Group",
                                "project", "agreement-service",
                                "projectPath", "Group/agreement-service",
                                "defaultBranch", "main"
                        ),
                        "references", map(
                                "systems", List.of("agreement-service"),
                                "boundedContexts", List.of("agreement-process-management"),
                                "terms", List.of("agreement")
                        ),
                        "sourceLayout", map(
                                "buildTool", "maven",
                                "sourceRoots", List.of("src/main/java"),
                                "importantPaths", List.of("agreement-api/src/main/java/com/example/agreement")
                        ),
                        "packagePrefixes", List.of("com.example.agreement"),
                        "classHints", List.of("AgreementApplication"),
                        "endpointHints", List.of("/agreements"),
                        "modules", List.of(map(
                                "id", "agreement-api",
                                "name", "Agreement API",
                                "lifecycleStatus", "active",
                                "sourceRoots", List.of("agreement-api/src/main/java"),
                                "importantPaths", List.of("agreement-api/src/main/java/com/example/agreement"),
                                "source", map(
                                        "paths", List.of("agreement-api/src/main/java"),
                                        "packages", List.of("com.example.agreement.api")
                                ),
                                "matchSignals", map("strong", map(
                                        "classHints", List.of("AgreementSubmitController")
                                ))
                        ))
                )),
                List.of(map(
                        "id", "agreement-service-scope",
                        "name", "Agreement Service Scope",
                        "target", map("processes", List.of("agreement-submit-process")),
                        "repositories", List.of(map(
                                "repoId", "agreement-service-repo",
                                "role", "primary",
                                "priority", 1,
                                "include", true,
                                "moduleIds", List.of("agreement-api"),
                                "reason", "Main implementation for the agreement submit flow."
                        )),
                        "packagePrefixes", List.of("com.example.agreement"),
                        "classHints", List.of("AgreementSubmitController"),
                        "endpointHints", List.of("/agreements"),
                        "databaseHints", map("tables", List.of("AGREEMENT_PROCESS")),
                        "searchStrategy", map(
                                "priorityOrder", List.of("agreement-service-repo"),
                                "includeSharedLibraries", true
                        ),
                        "limitations", List.of("Generated clients are outside this compact test catalog.")
                )),
                List.of(
                        map("id", "agreement-process-management", "name", "Agreement Process Management"),
                        map("id", "notifications", "name", "Notifications")
                ),
                List.of(new OperationalContextGlossaryTerm(
                        "agreement",
                        "Agreement",
                        "domain-term",
                        "Agreement local-language term.",
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
        );
    }

    private static Map<String, Object> map(Object... values) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
