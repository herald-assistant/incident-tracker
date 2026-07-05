package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.BOUNDED_CONTEXT_BOUNDARY;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.INSIDE_BOUNDED_CONTEXT;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.INSIDE_SYSTEM;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.SOURCE_INFERRED_FROM_TARGET_NAME;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.SOURCE_PARENT_SYSTEM_OWNERSHIP;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution.SYSTEM_BOUNDARY;

class OperationalContextOwnershipResolverTest {

    private final OperationalContextOwnershipResolver resolver = new OperationalContextOwnershipResolver();

    @Test
    void shouldPreferBoundedContextOwnerOverSystemOwner() {
        var resolution = resolver.resolve(sampleCatalog(), new OperationalContextOwnershipRequest(
                null,
                List.of("crm-customer-service"),
                List.of("customer-profile-context"),
                List.of(),
                List.of(),
                null
        ));

        assertEquals(INSIDE_BOUNDED_CONTEXT, resolution.situationType());
        assertEquals(1, resolution.primaryOwners().size());
        assertEquals("bounded-context", resolution.primaryOwners().get(0).targetType());
        assertEquals("customer-profile-context", resolution.primaryOwners().get(0).targetId());
        assertEquals(List.of("customer-domain-team"), resolution.primaryOwners().get(0).ownerTeamIds());
    }

    @Test
    void shouldFallbackFromBoundedContextToParentSystemOwner() {
        var resolution = resolver.resolve(sampleCatalog(), new OperationalContextOwnershipRequest(
                null,
                List.of(),
                List.of("customer-history-context"),
                List.of(),
                List.of(),
                null
        ));

        assertEquals(INSIDE_BOUNDED_CONTEXT, resolution.situationType());
        assertEquals("system", resolution.primaryOwners().get(0).targetType());
        assertEquals("crm-customer-service", resolution.primaryOwners().get(0).targetId());
        assertEquals(List.of("crm-platform-team"), resolution.primaryOwners().get(0).ownerTeamIds());
        assertEquals(SOURCE_PARENT_SYSTEM_OWNERSHIP, resolution.primaryOwners().get(0).source());
    }

    @Test
    void shouldInferSystemOwnerWhenExplicitOwnerIsMissing() {
        var resolution = resolver.resolve(sampleCatalog(), new OperationalContextOwnershipRequest(
                null,
                List.of("crm-case-gateway"),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertEquals(INSIDE_SYSTEM, resolution.situationType());
        assertEquals("crm-case-gateway", resolution.primaryOwners().get(0).targetId());
        assertEquals("wlasciciel systemu CRM Case Gateway", resolution.primaryOwners().get(0).ownerLabel());
        assertEquals(SOURCE_INFERRED_FROM_TARGET_NAME, resolution.primaryOwners().get(0).source());
        assertTrue(resolution.visibilityLimits().stream()
                .anyMatch(limit -> limit.contains("has no explicit owner")));
    }

    @Test
    void shouldReturnBothSidesForSystemBoundary() {
        var resolution = resolver.resolve(sampleCatalog(), new OperationalContextOwnershipRequest(
                null,
                List.of("crm-customer-service", "salesforce"),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertEquals(SYSTEM_BOUNDARY, resolution.situationType());
        assertEquals(List.of("crm-platform-team"), resolution.primaryOwners().get(0).ownerTeamIds());
        assertEquals("wlasciciel systemu Salesforce", resolution.partnerOwners().get(0).ownerLabel());
    }

    @Test
    void shouldResolveEndpointOwnerThroughRepositoryAndBoundedContext() {
        var technicalTarget = new OperationalContextOwnershipRequest.TechnicalTarget(
                "crm-customer-service-repo",
                null,
                List.of("customer-profile-context-scope"),
                List.of(),
                List.of(),
                new OperationalContextOwnershipRequest.EndpointTarget(
                        "GET",
                        "/api/customers/{id}",
                        "CustomerController",
                        "getCustomer"
                ),
                "endpoint-search"
        );

        var resolution = resolver.resolve(sampleCatalog(), new OperationalContextOwnershipRequest(
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                technicalTarget
        ));

        assertEquals(INSIDE_BOUNDED_CONTEXT, resolution.situationType());
        assertEquals("customer-profile-context", resolution.primaryOwners().get(0).targetId());
        assertEquals(List.of("customer-domain-team"), resolution.primaryOwners().get(0).ownerTeamIds());
        assertTrue(resolution.resolutionPath().stream()
                .anyMatch(step -> step.contains("technicalTarget.endpoint")));
        assertTrue(resolution.resolutionPath().stream()
                .anyMatch(step -> step.contains("repository:crm-customer-service-repo")));
    }

    @Test
    void shouldReturnBothSidesForBoundedContextBoundary() {
        var resolution = resolver.resolve(sampleCatalog(), new OperationalContextOwnershipRequest(
                BOUNDED_CONTEXT_BOUNDARY,
                List.of(),
                List.of("customer-profile-context", "customer-history-context"),
                List.of(),
                List.of(),
                null
        ));

        assertEquals(BOUNDED_CONTEXT_BOUNDARY, resolution.situationType());
        assertEquals("customer-profile-context", resolution.primaryOwners().get(0).targetId());
        assertEquals("crm-customer-service", resolution.partnerOwners().get(0).targetId());
    }

    private static OperationalContextDtos.OperationalContextCatalog sampleCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(
                        map(
                                "id", "crm-customer-service",
                                "name", "CRM Customer Service",
                                "ownership", ownership(List.of("crm-platform-team"), null, "high")
                        ),
                        map(
                                "id", "salesforce",
                                "name", "Salesforce",
                                "ownership", ownership(List.of(), "wlasciciel systemu Salesforce", "medium")
                        ),
                        map(
                                "id", "crm-case-gateway",
                                "name", "CRM Case Gateway"
                        )
                ),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service-repo",
                        "name", "CRM Customer Service Repository",
                        "git", map("projectPath", "CRM/customer-platform/crm-customer-service-repo"),
                        "references", map(
                                "systems", List.of("crm-customer-service"),
                                "boundedContexts", List.of("customer-profile-context")
                        )
                )),
                List.of(map(
                        "id", "customer-profile-context-scope",
                        "target", map("type", "bounded-context", "id", "customer-profile-context"),
                        "repositories", List.of(map("repoId", "crm-customer-service-repo"))
                )),
                List.of(
                        map(
                                "id", "customer-profile-context",
                                "name", "Customer Profile",
                                "references", map("systems", List.of("crm-customer-service")),
                                "ownership", ownership(List.of("customer-domain-team"), null, "high")
                        ),
                        map(
                                "id", "customer-history-context",
                                "name", "Customer History",
                                "references", map("systems", List.of("crm-customer-service"))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static Map<String, Object> ownership(List<String> ownerTeamIds, String ownerLabel, String confidence) {
        return map(
                "ownerTeamIds", ownerTeamIds,
                "ownerLabel", ownerLabel,
                "ownershipStatus", "explicit",
                "confidence", confidence,
                "source", "test"
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
