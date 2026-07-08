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
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.CODE_SEARCH_SCOPE;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.REPOSITORY;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.SYSTEM;

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
        assertEquals("crm-customer-profile", systems.get(1).systemId());

        var catalog = systems.get(1);
        assertEquals("CRM Customer Profile", catalog.name());
        assertEquals("internal-application", catalog.kind());
        assertEquals("active", catalog.lifecycleStatus());
        assertEquals("healthy", catalog.operationalStatus());
        assertEquals("high", catalog.criticality());
        assertEquals("Handles CRM customer profile operations.", catalog.summary());
        assertEquals(List.of("crm-customer-profile", "notifications"), catalog.aliases());
        assertEquals(2, catalog.repositoryCount());
        assertEquals(1, catalog.codeSearchScopeCount());
        assertEquals(List.of("crm-customer-profile-team"), catalog.ownerTeamIds());

        assertFalse(capturedQuery.get().includeIndexDocument());
        assertTrue(capturedQuery.get().includes(SYSTEM));
        assertTrue(capturedQuery.get().includes(REPOSITORY));
        assertTrue(capturedQuery.get().includes(CODE_SEARCH_SCOPE));
    }

    private static OperationalContextCatalog catalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(
                        map(
                                "id", "platform-team",
                                "name", "Platform Team",
                                "references", map("systems", List.of("crm-customer-profile"))
                        ),
                        map(
                                "id", "ops-team",
                                "name", "Ops Team"
                        )
                ),
                List.of(),
                List.of(
                        map(
                                "id", "crm-customer-profile",
                                "name", "CRM Customer Profile",
                                "shortName", "CRM Customer Profile",
                                "kind", "internal-application",
                                "lifecycleStatus", "active",
                                "operationalStatus", "healthy",
                                "criticality", "high",
                                "summary", "Handles CRM customer profile operations.",
                                "aliases", List.of("crm-customer-profile", "notifications"),
                                "references", map(
                                        "repositories", List.of("crm-customer-profile-worker"),
                                        "teams", List.of("crm-customer-profile-team")
                                ),
                                "ownership", ownership(List.of("crm-customer-profile-team"), "high"),
                                "codeSearchScope", map("repositories", List.of("crm-customer-profile-worker"))
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
                        map("id", "crm-customer-profile-api", "name", "CRM Customer Profile API"),
                        map("id", "crm-customer-profile-worker", "name", "CRM Customer Profile Worker"),
                        map("id", "crm-customer-profile-domain", "name", "CRM Customer Profile Domain")
                ),
                List.of(map(
                        "id", "crm-customer-profile-semantic-scope",
                        "name", "Catalog semantic scope",
                        "target", map("type", "system", "id", "crm-customer-profile"),
                        "repositories", List.of(
                                map("repoId", "crm-customer-profile-api", "searchMode", "whole-repository"),
                                map("repoId", "crm-customer-profile-domain", "searchMode", "whole-repository")
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

    private static Map<String, Object> ownership(List<String> ownerTeamIds, String confidence) {
        return map(
                "ownerTeamIds", ownerTeamIds,
                "ownershipStatus", "explicit",
                "confidence", confidence,
                "source", "test"
        );
    }
}
