package pl.mkn.incidenttracker.features.flowexplorer.context;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextQuery;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextEntryType.REPOSITORY;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextEntryType.SYSTEM;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextEntryType.TEAM;

class FlowExplorerSystemSelectionServiceTest {

    @Test
    void shouldReturnInternalSystemsWithCatalogSignalsForUiSelection() {
        var capturedQuery = new AtomicReference<OperationalContextQuery>();
        var service = new FlowExplorerSystemSelectionService(query -> {
            capturedQuery.set(query);
            return catalog();
        });

        var systems = service.systems();

        assertEquals(2, systems.size());
        assertEquals("api-gateway", systems.get(0).systemId());
        assertEquals("billing-core", systems.get(1).systemId());

        var billing = systems.get(1);
        assertEquals("Billing Core", billing.name());
        assertEquals("internal-application", billing.kind());
        assertEquals("active", billing.lifecycleStatus());
        assertEquals("healthy", billing.operationalStatus());
        assertEquals("high", billing.criticality());
        assertEquals("Handles billing operations.", billing.summary());
        assertEquals(List.of("billing", "payments"), billing.aliases());
        assertEquals(3, billing.repositoryCount());
        assertEquals(2, billing.codeSearchScopeCount());
        assertEquals(List.of("billing-team", "platform-team", "ops-team"), billing.ownerTeamIds());

        assertFalse(capturedQuery.get().includeIndexDocument());
        assertTrue(capturedQuery.get().includes(SYSTEM));
        assertTrue(capturedQuery.get().includes(TEAM));
        assertTrue(capturedQuery.get().includes(REPOSITORY));
    }

    private static OperationalContextCatalog catalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(
                        map(
                                "id", "platform-team",
                                "name", "Platform Team",
                                "references", map("systems", List.of("billing-core"))
                        ),
                        map(
                                "id", "ops-team",
                                "name", "Ops Team",
                                "responsibilities", List.of(map(
                                        "targetType", "system",
                                        "targetId", "billing-core"
                                ))
                        )
                ),
                List.of(),
                List.of(
                        map(
                                "id", "billing-core",
                                "name", "Billing Core",
                                "shortName", "Billing",
                                "kind", "internal-application",
                                "lifecycleStatus", "active",
                                "operationalStatus", "healthy",
                                "criticality", "high",
                                "summary", "Handles billing operations.",
                                "aliases", List.of("billing", "payments"),
                                "references", map(
                                        "repositories", List.of("billing-api"),
                                        "teams", List.of("billing-team")
                                ),
                                "responsibilities", List.of(map(
                                        "actorType", "team",
                                        "actorId", "platform-team"
                                )),
                                "codeSearchScope", map("repositories", List.of("billing-worker"))
                        ),
                        map(
                                "id", "api-gateway",
                                "name", "API Gateway",
                                "kind", "api-gateway"
                        ),
                        map(
                                "id", "payment-provider",
                                "name", "Payment Provider",
                                "kind", "external-saas"
                        )
                ),
                List.of(),
                List.of(
                        map("id", "billing-api", "name", "Billing API"),
                        map("id", "billing-worker", "name", "Billing Worker"),
                        map("id", "billing-domain", "name", "Billing Domain")
                ),
                List.of(map(
                        "id", "billing-semantic-scope",
                        "name", "Billing semantic scope",
                        "target", map("type", "system", "id", "billing-core"),
                        "repositories", List.of(
                                map("repoId", "billing-api"),
                                map("repoId", "billing-domain")
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static Map<String, Object> map(Object... values) {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
