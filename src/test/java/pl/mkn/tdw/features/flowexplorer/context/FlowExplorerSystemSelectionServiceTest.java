package pl.mkn.tdw.features.flowexplorer.context;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.REPOSITORY;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.SYSTEM;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.TEAM;

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
        assertEquals("catalog-core", systems.get(1).systemId());

        var catalog = systems.get(1);
        assertEquals("Catalog Core", catalog.name());
        assertEquals("internal-application", catalog.kind());
        assertEquals("active", catalog.lifecycleStatus());
        assertEquals("healthy", catalog.operationalStatus());
        assertEquals("high", catalog.criticality());
        assertEquals("Handles catalog operations.", catalog.summary());
        assertEquals(List.of("catalog", "notifications"), catalog.aliases());
        assertEquals(3, catalog.repositoryCount());
        assertEquals(2, catalog.codeSearchScopeCount());
        assertEquals(List.of("catalog-team", "platform-team", "ops-team"), catalog.ownerTeamIds());

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
                                "references", map("systems", List.of("catalog-core"))
                        ),
                        map(
                                "id", "ops-team",
                                "name", "Ops Team",
                                "responsibilities", List.of(map(
                                        "targetType", "system",
                                        "targetId", "catalog-core"
                                ))
                        )
                ),
                List.of(),
                List.of(
                        map(
                                "id", "catalog-core",
                                "name", "Catalog Core",
                                "shortName", "Catalog",
                                "kind", "internal-application",
                                "lifecycleStatus", "active",
                                "operationalStatus", "healthy",
                                "criticality", "high",
                                "summary", "Handles catalog operations.",
                                "aliases", List.of("catalog", "notifications"),
                                "references", map(
                                        "repositories", List.of("catalog-api"),
                                        "teams", List.of("catalog-team")
                                ),
                                "responsibilities", List.of(map(
                                        "actorType", "team",
                                        "actorId", "platform-team"
                                )),
                                "codeSearchScope", map("repositories", List.of("catalog-worker"))
                        ),
                        map(
                                "id", "api-gateway",
                                "name", "API Gateway",
                                "kind", "api-gateway"
                        ),
                        map(
                                "id", "notification-gateway",
                                "name", "Notification Gateway",
                                "kind", "external-saas"
                        )
                ),
                List.of(),
                List.of(
                        map("id", "catalog-api", "name", "Catalog API"),
                        map("id", "catalog-worker", "name", "Catalog Worker"),
                        map("id", "catalog-domain", "name", "Catalog Domain")
                ),
                List.of(map(
                        "id", "catalog-semantic-scope",
                        "name", "Catalog semantic scope",
                        "target", map("type", "system", "id", "catalog-core"),
                        "repositories", List.of(
                                map("repoId", "catalog-api"),
                                map("repoId", "catalog-domain")
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
