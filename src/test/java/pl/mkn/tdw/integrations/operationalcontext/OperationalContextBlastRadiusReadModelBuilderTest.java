package pl.mkn.tdw.integrations.operationalcontext;

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
        var model = builder.buildForEntity(sampleCatalog(), "endpoint", "/crm/customers");

        assertEquals("operational-context.blast-radius", model.contract());
        assertEquals("endpoint", model.analysisTarget().type());
        assertEquals("/crm/customers", model.analysisTarget().id());
        assertEquals(1, model.impactedFlows().size());
        assertEquals("customer-profile-update-process", model.impactedFlows().get(0).flow().id());
        assertEquals(4, model.impactedFlows().get(0).impactedSteps().size());
        assertEquals("direct-hit", model.impactedFlows().get(0).impactedSteps().get(0).impactType());
        assertTrue(model.impactedSystems().stream()
                .anyMatch(system -> system.entity().id().equals("notification-service")));
        assertTrue(model.impactedIntegrations().stream()
                .anyMatch(integration -> integration.entity().id().equals("notifications-to-crm-provider")));
        assertTrue(model.impactedDataStores().stream()
                .anyMatch(dataStore -> dataStore.entity().id().equals("customer-profile-db")));
        assertFalse(model.impactedImplementations().isEmpty());
        assertTrue(model.suggestedNextEvidence().stream()
                .anyMatch(suggestion -> suggestion.contains("code-search scopes")));
    }

    @Test
    void shouldProjectBlastRadiusFromCodeSearchScopeTarget() {
        var model = builder.buildForEntity(sampleCatalog(), "code-search-scope", "crm-customer-service-scope");

        assertEquals("code-search-scope", model.analysisTarget().type());
        assertEquals(1, model.impactedFlows().size());
        assertTrue(model.impactedFlows().get(0).impactedSteps().stream()
                .anyMatch(step -> step.implementations().stream()
                        .anyMatch(implementation -> implementation.codeSearchScope().id().equals("crm-customer-service-scope"))));
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
                        "id", "customer-profile-update-process",
                        "name", "Customer Profile Update Process",
                        "participants", map(
                                "primarySystems", List.of("crm-customer-service"),
                                "supportingSystems", List.of("notification-service"),
                                "externalSystems", List.of("crm-operator-ui", "notification-provider-b"),
                                "platformComponents", List.of("customer-profile-db")
                        ),
                        "references", map(
                                "systems", List.of("crm-operator-ui", "crm-customer-service", "notification-service", "notification-provider-b"),
                                "boundedContexts", List.of("customer-profile-management", "notifications"),
                                "integrations", List.of("customer-to-notifications", "notifications-to-crm-provider"),
                                "dataStores", List.of("customer-profile-db")
                        ),
                        "steps", List.of(
                                map(
                                        "id", "receive-submit",
                                        "name", "Receive Submit Request",
                                        "references", map(
                                                "systems", List.of("crm-operator-ui", "crm-customer-service"),
                                                "boundedContexts", List.of("customer-profile-management")
                                        ),
                                        "match", map(
                                                "endpointPrefixes", List.of("/crm/customers"),
                                                "classHints", List.of("CustomerProfileController")
                                        )
                                ),
                                map(
                                        "id", "persist-state",
                                        "name", "Persist Process State",
                                        "references", map(
                                                "systems", List.of("crm-customer-service"),
                                                "boundedContexts", List.of("customer-profile-management"),
                                                "dataStores", List.of("customer-profile-db")
                                        ),
                                        "match", map("tables", List.of("CUSTOMER_PROFILE"))
                                ),
                                map(
                                        "id", "request-notification",
                                        "name", "Request Notification",
                                        "references", map(
                                                "systems", List.of("crm-customer-service", "notification-service"),
                                                "boundedContexts", List.of("customer-profile-management", "notifications"),
                                                "integrations", List.of("customer-to-notifications")
                                        )
                                ),
                                map(
                                        "id", "deliver-external",
                                        "name", "Deliver External Message",
                                        "references", map(
                                                "systems", List.of("notification-service", "notification-provider-b"),
                                                "boundedContexts", List.of("notifications"),
                                                "integrations", List.of("notifications-to-crm-provider")
                                        ),
                                        "match", map("topics", List.of("external.notifications"))
                                )
                        )
                )),
                List.of(
                        map("id", "crm-operator-ui"),
                        map("id", "crm-customer-service"),
                        map("id", "notification-service"),
                        map("id", "notification-provider-b")
                ),
                List.of(
                        map(
                                "id", "customer-to-notifications",
                                "participants", map(
                                        "source", map("system", "crm-customer-service", "boundedContext", "customer-profile-management"),
                                        "targets", List.of(map("system", "notification-service", "boundedContext", "notifications"))
                                ),
                                "transport", map("http", map("endpointPrefixes", List.of("/notifications")))
                        ),
                        map(
                                "id", "notifications-to-crm-provider",
                                "participants", map(
                                        "source", map("system", "notification-service", "boundedContext", "notifications"),
                                        "targets", List.of(map("system", "notification-provider-b"))
                                ),
                                "transport", map("messaging", map("topics", List.of("external.notifications")))
                        )
                ),
                List.of(map(
                        "id", "crm-customer-service-repo",
                        "git", map("projectPath", "Group/crm-customer-service"),
                        "modules", List.of(map(
                                "id", "customer-module",
                                "sourceRoots", List.of("customer-module/src/main/java"),
                                "source", map("packages", List.of("com.example.crm.customer"))
                        ))
                )),
                List.of(map(
                        "id", "crm-customer-service-scope",
                        "target", map("type", "process", "id", "customer-profile-update-process"),
                        "repositories", List.of(map(
                                "repoId", "crm-customer-service-repo",
                                "role", "primary-implementation",
                                "priority", 1,
                                "moduleIds", List.of("customer-module")
                        )),
                        "hints", map(
                                "endpointHints", List.of("/crm/customers"),
                                "database", map("tables", List.of("CUSTOMER_PROFILE"))
                        )
                )),
                List.of(
                        map("id", "customer-profile-management"),
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
