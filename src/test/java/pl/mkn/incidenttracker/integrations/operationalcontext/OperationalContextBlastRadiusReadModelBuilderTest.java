package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextBlastRadiusReadModelBuilderTest {

    private final OperationalContextBlastRadiusReadModelBuilder builder =
            new OperationalContextBlastRadiusReadModelBuilder();

    @Test
    void shouldProjectBlastRadiusFromEndpointToDownstreamFlow() {
        var model = builder.buildForEntity(sampleCatalog(), "endpoint", "/agreements");

        assertEquals("operational-context.blast-radius", model.contract());
        assertEquals("endpoint", model.analysisTarget().type());
        assertEquals("/agreements", model.analysisTarget().id());
        assertEquals(1, model.impactedFlows().size());
        assertEquals("agreement-submit-process", model.impactedFlows().get(0).flow().id());
        assertEquals(4, model.impactedFlows().get(0).impactedSteps().size());
        assertEquals("direct-hit", model.impactedFlows().get(0).impactedSteps().get(0).impactType());
        assertTrue(model.impactedSystems().stream()
                .anyMatch(system -> system.entity().id().equals("notification-service")));
        assertTrue(model.impactedIntegrations().stream()
                .anyMatch(integration -> integration.entity().id().equals("notifications-to-external-b")));
        assertTrue(model.impactedDataStores().stream()
                .anyMatch(dataStore -> dataStore.entity().id().equals("agreement-db")));
        assertFalse(model.impactedImplementations().isEmpty());
        assertTrue(model.suggestedNextEvidence().stream()
                .anyMatch(suggestion -> suggestion.contains("code-search scopes")));
    }

    @Test
    void shouldProjectBlastRadiusFromCodeSearchScopeTarget() {
        var model = builder.buildForEntity(sampleCatalog(), "code-search-scope", "agreement-service-scope");

        assertEquals("code-search-scope", model.analysisTarget().type());
        assertEquals(1, model.impactedFlows().size());
        assertTrue(model.impactedFlows().get(0).impactedSteps().stream()
                .anyMatch(step -> step.implementations().stream()
                        .anyMatch(implementation -> implementation.codeSearchScope().id().equals("agreement-service-scope"))));
    }

    @Test
    void shouldReportMissingFlowForUnknownSignal() {
        var model = builder.buildForEntity(sampleCatalog(), "class", "MissingController");

        assertTrue(model.impactedFlows().isEmpty());
        assertTrue(model.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("BLAST_RADIUS_NO_MATCHING_FLOW")));
    }

    private static OperationalContextDtos.OperationalContextCatalog sampleCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map(
                        "id", "agreement-submit-process",
                        "name", "Agreement Submit Process",
                        "participants", map(
                                "primarySystems", List.of("agreement-service"),
                                "supportingSystems", List.of("notification-service"),
                                "externalSystems", List.of("operator-frontend", "external-system-b"),
                                "platformComponents", List.of("agreement-db")
                        ),
                        "references", map(
                                "systems", List.of("operator-frontend", "agreement-service", "notification-service", "external-system-b"),
                                "boundedContexts", List.of("agreement-process-management", "notifications"),
                                "integrations", List.of("agreement-to-notifications", "notifications-to-external-b"),
                                "dataStores", List.of("agreement-db")
                        ),
                        "steps", List.of(
                                map(
                                        "id", "receive-submit",
                                        "name", "Receive Submit Request",
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
                                        "references", map(
                                                "systems", List.of("agreement-service"),
                                                "boundedContexts", List.of("agreement-process-management"),
                                                "dataStores", List.of("agreement-db")
                                        ),
                                        "match", map("tables", List.of("AGREEMENT_PROCESS"))
                                ),
                                map(
                                        "id", "request-notification",
                                        "name", "Request Notification",
                                        "references", map(
                                                "systems", List.of("agreement-service", "notification-service"),
                                                "boundedContexts", List.of("agreement-process-management", "notifications"),
                                                "integrations", List.of("agreement-to-notifications")
                                        )
                                ),
                                map(
                                        "id", "deliver-external",
                                        "name", "Deliver External Message",
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
                        map("id", "operator-frontend"),
                        map("id", "agreement-service"),
                        map("id", "notification-service"),
                        map("id", "external-system-b")
                ),
                List.of(
                        map(
                                "id", "agreement-to-notifications",
                                "participants", map(
                                        "source", map("system", "agreement-service", "boundedContext", "agreement-process-management"),
                                        "targets", List.of(map("system", "notification-service", "boundedContext", "notifications"))
                                ),
                                "transport", map("http", map("endpointPrefixes", List.of("/notifications")))
                        ),
                        map(
                                "id", "notifications-to-external-b",
                                "participants", map(
                                        "source", map("system", "notification-service", "boundedContext", "notifications"),
                                        "targets", List.of(map("system", "external-system-b"))
                                ),
                                "transport", map("messaging", map("topics", List.of("external.notifications")))
                        )
                ),
                List.of(map(
                        "id", "agreement-service-repo",
                        "git", map("projectPath", "Group/agreement-service"),
                        "modules", List.of(map(
                                "id", "agreement-module",
                                "sourceRoots", List.of("agreement-module/src/main/java"),
                                "source", map("packages", List.of("com.example.agreement.process"))
                        ))
                )),
                List.of(map(
                        "id", "agreement-service-scope",
                        "target", map(
                                "systems", List.of("agreement-service"),
                                "processes", List.of("agreement-submit-process"),
                                "boundedContexts", List.of("agreement-process-management")
                        ),
                        "repositories", List.of(map(
                                "repoId", "agreement-service-repo",
                                "role", "primary",
                                "priority", 1,
                                "include", true,
                                "moduleIds", List.of("agreement-module")
                        )),
                        "endpointHints", List.of("/agreements"),
                        "databaseHints", map("tables", List.of("AGREEMENT_PROCESS"))
                )),
                List.of(
                        map("id", "agreement-process-management"),
                        map("id", "notifications")
                ),
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
