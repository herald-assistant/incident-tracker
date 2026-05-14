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
        var model = builder.buildForEntity(sampleCatalog(), "process", "agreement-submit-process");

        assertEquals("operational-context.flow", model.contract());
        assertEquals("agreement-submit-process", model.analysisTarget().id());
        assertEquals("http", model.trigger().channel());
        assertTrue(model.trigger().endpoints().contains("POST /agreements/{agreementId}/submit"));
        assertEquals(4, model.steps().size());
        assertEquals(3, model.edges().size());
        assertTrue(model.validationFindings().isEmpty());

        var receive = model.steps().get(0);
        assertEquals("receive-submit", receive.id());
        assertEquals("http-endpoint", receive.kind());
        assertTrue(receive.systems().stream().anyMatch(system -> system.id().equals("operator-frontend")));
        assertTrue(receive.systems().stream().anyMatch(system -> system.id().equals("agreement-service")));
        assertTrue(receive.boundedContexts().stream()
                .anyMatch(context -> context.id().equals("agreement-process-management")));
        assertTrue(receive.implementations().stream()
                .anyMatch(implementation -> implementation.id().equals("agreement-service-scope::agreement-service-repo::agreement-module")));
        assertTrue(receive.codeSearchScopes().stream()
                .anyMatch(scope -> scope.id().equals("agreement-service-scope")));
        assertTrue(receive.codeHints().endpointHints().contains("/agreements"));
        assertTrue(receive.codeHints().classHints().contains("AgreementSubmitController"));

        var persist = model.steps().get(1);
        assertEquals("database-write", persist.kind());
        assertTrue(persist.dataStores().stream().anyMatch(store -> store.id().equals("agreement-db")));
        assertTrue(persist.codeHints().databaseHints().tables().contains("AGREEMENT_PROCESS"));
        assertTrue(persist.classHints().contains("AgreementProcessRepository"));

        var notify = model.steps().get(2);
        assertEquals("integration-call", notify.kind());
        assertTrue(notify.integrations().stream()
                .anyMatch(integration -> integration.id().equals("agreement-to-notifications")));
        assertTrue(notify.integrationHints().endpoints().contains("/notifications"));
        assertTrue(notify.boundedContexts().stream().anyMatch(context -> context.id().equals("notifications")));

        var external = model.steps().get(3);
        assertEquals("integration-call", external.kind());
        assertTrue(external.integrations().stream()
                .anyMatch(integration -> integration.id().equals("notifications-to-external-b")));
        assertTrue(external.integrationHints().topics().contains("external.notifications"));
        assertTrue(model.involvedIntegrations().stream()
                .anyMatch(integration -> integration.id().equals("notifications-to-external-b")));

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
        var model = builder.buildForEntity(sampleCatalog(), "system", "agreement-service");

        assertTrue(model.steps().isEmpty());
        assertTrue(model.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("FLOW_MODEL_TARGET_NOT_PROCESS")));
    }

    private static OperationalContextDtos.OperationalContextCatalog sampleCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map(
                        "id", "agreement-submit-process",
                        "name", "Agreement Submit Process",
                        "type", "request-flow",
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
                        "lifecycle", map(
                                "triggers", List.of(map(
                                        "type", "api",
                                        "name", "Agreement submit",
                                        "endpoint", "POST /agreements/{agreementId}/submit"
                                ))
                        ),
                        "steps", List.of(
                                map(
                                        "id", "receive-submit",
                                        "name", "Receive Submit Request",
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
                                                "boundedContexts", List.of("agreement-process-management"),
                                                "dataStores", List.of("agreement-db")
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
                                        "match", map(
                                                "classHints", List.of("NotificationPort")
                                        )
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
                                        "match", map(
                                                "topics", List.of("external.notifications")
                                        )
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
                                "integrationStyle", "sync-http",
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
                                "implementation", map(
                                        "classHints", List.of("NotificationClient")
                                )
                        ),
                        map(
                                "id", "notifications-to-external-b",
                                "integrationStyle", "async-event",
                                "participants", map(
                                        "source", map("system", "notification-service", "boundedContext", "notifications"),
                                        "targets", List.of(map("system", "external-system-b"))
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
                        "id", "agreement-service-repo",
                        "name", "Agreement Service Repository",
                        "git", map("projectPath", "Group/agreement-service"),
                        "packagePrefixes", List.of("com.example.agreement"),
                        "classHints", List.of("AgreementApplication"),
                        "endpointHints", List.of("/agreements"),
                        "modules", List.of(map(
                                "id", "agreement-module",
                                "name", "Agreement Module",
                                "sourceRoots", List.of("agreement-module/src/main/java"),
                                "source", map(
                                        "paths", List.of("agreement-module/src/main/java"),
                                        "packages", List.of("com.example.agreement.process")
                                ),
                                "matchSignals", map("strong", map(
                                        "classHints", List.of("AgreementSubmitController")
                                ))
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
                                "moduleIds", List.of("agreement-module"),
                                "reason", "Main agreement implementation"
                        )),
                        "packagePrefixes", List.of("com.example.agreement"),
                        "classHints", List.of("AgreementSubmitController"),
                        "endpointHints", List.of("/agreements"),
                        "databaseHints", map(
                                "tables", List.of("AGREEMENT_PROCESS")
                        )
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
