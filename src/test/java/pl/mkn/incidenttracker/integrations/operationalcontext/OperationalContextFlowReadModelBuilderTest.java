package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextFlowReadModelBuilderTest {

    private final OperationalContextFlowReadModelBuilder builder =
            new OperationalContextFlowReadModelBuilder();

    @Test
    void shouldProjectRequestFlowFromProcessStepsImplementationsAndIntegrations() {
        var model = builder.buildForEntity(sampleCatalog(), "process", "customer-profile-update-process");

        assertEquals("operational-context.flow", model.contract());
        assertEquals("customer-profile-update-process", model.analysisTarget().id());
        assertEquals("http", model.trigger().channel());
        assertTrue(model.trigger().endpoints().contains("POST /crm/customers/{customerId}/profile"));
        assertEquals(4, model.steps().size());
        assertEquals(3, model.edges().size());
        assertTrue(model.validationFindings().isEmpty());

        var receive = model.steps().get(0);
        assertEquals("receive-submit", receive.id());
        assertEquals("http-endpoint", receive.kind());
        assertTrue(receive.systems().stream().anyMatch(system -> system.id().equals("crm-operator-ui")));
        assertTrue(receive.systems().stream().anyMatch(system -> system.id().equals("crm-customer-service")));
        assertTrue(receive.boundedContexts().stream()
                .anyMatch(context -> context.id().equals("customer-profile-management")));
        assertTrue(receive.implementations().stream()
                .anyMatch(implementation -> implementation.id().equals("crm-customer-service-scope::crm-customer-service-repo::customer-module")));
        assertTrue(receive.codeSearchScopes().stream()
                .anyMatch(scope -> scope.id().equals("crm-customer-service-scope")));
        assertTrue(receive.codeHints().endpointHints().contains("/crm/customers"));
        assertTrue(receive.codeHints().classHints().contains("CustomerProfileController"));

        var persist = model.steps().get(1);
        assertEquals("database-write", persist.kind());
        assertTrue(persist.dataStores().stream().anyMatch(store -> store.id().equals("customer-profile-db")));
        assertTrue(persist.codeHints().databaseHints().tables().contains("CUSTOMER_PROFILE"));
        assertTrue(persist.classHints().contains("CustomerProfileRepository"));

        var notify = model.steps().get(2);
        assertEquals("integration-call", notify.kind());
        assertTrue(notify.integrations().stream()
                .anyMatch(integration -> integration.id().equals("customer-to-notifications")));
        assertTrue(notify.integrationHints().endpoints().contains("/notifications"));
        assertTrue(notify.boundedContexts().stream().anyMatch(context -> context.id().equals("notifications")));

        var external = model.steps().get(3);
        assertEquals("integration-call", external.kind());
        assertTrue(external.integrations().stream()
                .anyMatch(integration -> integration.id().equals("notifications-to-crm-provider")));
        assertTrue(external.integrationHints().topics().contains("external.notifications"));
        assertTrue(model.involvedIntegrations().stream()
                .anyMatch(integration -> integration.id().equals("notifications-to-crm-provider")));

        assertEquals("receive-submit", model.edges().get(0).sourceStepId());
        assertEquals("persist-state", model.edges().get(0).targetStepId());
        assertFalse(model.edges().get(0).viaEntities().isEmpty());
    }

    @Test
    void shouldReportMissingStepsForProcessFlow() {
        var catalog = OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map("id", "empty-process")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );

        var model = builder.buildForEntity(catalog, "process", "empty-process");

        assertTrue(model.steps().isEmpty());
        assertTrue(model.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("FLOW_WITHOUT_STEPS")));
    }

    @Test
    void shouldWarnWhenTargetIsNotAProcess() {
        var model = builder.buildForEntity(sampleCatalog(), "system", "crm-customer-service");

        assertTrue(model.steps().isEmpty());
        assertTrue(model.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("FLOW_MODEL_TARGET_NOT_PROCESS")));
    }

    private static OperationalContextDtos.OperationalContextCatalog sampleCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map(
                        "id", "customer-profile-update-process",
                        "name", "Customer Profile Update Process",
                        "type", "request-flow",
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
                        "lifecycle", map(
                                "triggers", List.of(map(
                                        "type", "api",
                                        "name", "Customer profile update",
                                        "endpoint", "POST /crm/customers/{customerId}/profile"
                                ))
                        ),
                        "steps", List.of(
                                map(
                                        "id", "receive-submit",
                                        "name", "Receive Submit Request",
                                        "summary", "Frontend submits customer profile update.",
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
                                        "type", "database-write",
                                        "summary", "Write process state into customer profile database.",
                                        "references", map(
                                                "systems", List.of("crm-customer-service"),
                                                "boundedContexts", List.of("customer-profile-management"),
                                                "dataStores", List.of("customer-profile-db")
                                        ),
                                        "match", map(
                                                "classHints", List.of("CustomerProfileRepository"),
                                                "tables", List.of("CUSTOMER_PROFILE")
                                        )
                                ),
                                map(
                                        "id", "request-notification",
                                        "name", "Request Notification",
                                        "summary", "Customer profile process asks notifications capability to send a message.",
                                        "references", map(
                                                "systems", List.of("crm-customer-service", "notification-service"),
                                                "boundedContexts", List.of("customer-profile-management", "notifications"),
                                                "integrations", List.of("customer-to-notifications")
                                        ),
                                        "match", map(
                                                "classHints", List.of("NotificationPort")
                                        )
                                ),
                                map(
                                        "id", "deliver-external",
                                        "name", "Deliver External Message",
                                        "summary", "Notifications publishes the outbound message to external system B.",
                                        "references", map(
                                                "systems", List.of("notification-service", "notification-provider-b"),
                                                "boundedContexts", List.of("notifications"),
                                                "integrations", List.of("notifications-to-crm-provider")
                                        ),
                                        "match", map(
                                                "topics", List.of("external.notifications")
                                        )
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
                                "integrationStyle", "sync-http",
                                "participants", map(
                                        "source", map("system", "crm-customer-service", "boundedContext", "customer-profile-management"),
                                        "targets", List.of(map("system", "notification-service", "boundedContext", "notifications"))
                                ),
                                "transport", map(
                                        "protocols", List.of("http"),
                                        "http", map(
                                                "methods", List.of("POST"),
                                                "endpointPrefixes", List.of("/notifications")
                                        )
                                ),
                                "implementation", map(
                                        "classHints", List.of("NotificationClient")
                                )
                        ),
                        map(
                                "id", "notifications-to-crm-provider",
                                "integrationStyle", "async-event",
                                "participants", map(
                                        "source", map("system", "notification-service", "boundedContext", "notifications"),
                                        "targets", List.of(map("system", "notification-provider-b"))
                                ),
                                "transport", map(
                                        "protocols", List.of("messaging"),
                                        "messaging", map(
                                                "topics", List.of("external.notifications")
                                        )
                                ),
                                "implementation", map(
                                        "classHints", List.of("ExternalNotificationPublisher")
                                )
                        )
                ),
                List.of(map(
                        "id", "crm-customer-service-repo",
                        "name", "CRM Customer Service Repository",
                        "git", map("projectPath", "Group/crm-customer-service"),
                        "packagePrefixes", List.of("com.example.crm.customer"),
                        "classHints", List.of("CustomerApplication"),
                        "endpointHints", List.of("/crm/customers"),
                        "modules", List.of(map(
                                "id", "customer-module",
                                "name", "Customer Module",
                                "sourceRoots", List.of("customer-module/src/main/java"),
                                "source", map(
                                        "paths", List.of("customer-module/src/main/java"),
                                        "packages", List.of("com.example.crm.customer")
                                ),
                                "matchSignals", map("strong", map(
                                        "classHints", List.of("CustomerProfileController")
                                ))
                        ))
                )),
                List.of(map(
                        "id", "crm-customer-service-scope",
                        "target", map(
                                "type", "process",
                                "id", "customer-profile-update-process"
                        ),
                        "repositories", List.of(map(
                                "repoId", "crm-customer-service-repo",
                                "role", "primary-implementation",
                                "priority", 1,
                                "moduleIds", List.of("customer-module"),
                                "reason", "Main customer profile implementation"
                        )),
                        "hints", map(
                                "packagePrefixes", List.of("com.example.crm.customer"),
                                "classHints", List.of("CustomerProfileController"),
                                "endpointHints", List.of("/crm/customers"),
                                "database", map(
                                        "tables", List.of("CUSTOMER_PROFILE")
                                )
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
