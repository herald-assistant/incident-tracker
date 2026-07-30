package pl.mkn.tdw.features.runtimeconfigurationverification.deep;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeGrounding;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeRefSource;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeUsageKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContextStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflight;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflightStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepRepositoryScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationGroundingConfidence;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDifference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolver;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuntimeConfigurationDeepContextServiceTest {

    @Test
    void shouldNotCallDeepCapabilitiesInBasicMode() {
        var preflightService = mock(RuntimeConfigurationDeepPreflightService.class);
        var codeSearch = mock(RuntimeConfigurationCodeUsageSearchService.class);
        var operationalContext = mock(OperationalContextPort.class);
        var ownership = mock(OperationalContextOwnershipResolver.class);
        var service = new RuntimeConfigurationDeepContextService(
                preflightService,
                codeSearch,
                operationalContext,
                ownership
        );

        var result = service.build(
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "backend",
                null,
                deterministicContext("notifications.endpoint")
        );

        assertTrue(result.isEmpty());
        verifyNoInteractions(preflightService, codeSearch, operationalContext, ownership);
    }

    @Test
    void shouldResolveMultipleAffectedSystemsCodeGroundingAndPartnerOwnership() {
        var preflightService = mock(RuntimeConfigurationDeepPreflightService.class);
        when(preflightService.check("runtime-config", "backend", "release-42"))
                .thenReturn(preflight());
        var codeSearch = mock(RuntimeConfigurationCodeUsageSearchService.class);
        when(codeSearch.search(any(), any())).thenReturn(codeResult());
        var operationalContext = mock(OperationalContextPort.class);
        when(operationalContext.loadContext(any())).thenReturn(relatedCatalog());
        var service = new RuntimeConfigurationDeepContextService(
                preflightService,
                codeSearch,
                operationalContext,
                new OperationalContextOwnershipResolver()
        );

        var result = service.build(
                RuntimeConfigurationVerificationMode.DEEP,
                "runtime-config",
                "backend",
                "release-42",
                deterministicContext("notifications.endpoint")
        ).orElseThrow();

        assertEquals(RuntimeConfigurationDeepContextStatus.PARTIAL, result.status());
        assertEquals(2, result.affectedSystems().size());
        assertTrue(result.affectedSystems().stream()
                .anyMatch(system -> system.entityId().equals("notification")
                        && system.codeGroundingIds().contains("code-grounding-001")));
        assertEquals("customer-notification", result.integrations().get(0).entityId());
        assertEquals("customer-contact", result.processes().get(0).entityId());
        assertEquals("system-boundary", result.ownership().situationType());
        assertFalse(result.ownership().primaryOwners().isEmpty());
        assertFalse(result.ownership().partnerOwners().isEmpty());
        assertEquals(List.of("notification"), result.coverage().systemsWithoutCodeSearchScope());
        assertEquals("release-42", result.codeGrounding().get(0).usedRef());
    }

    @Test
    void shouldKeepUnknownOwnerVisibleAndMarkSignalOnlyOwnershipAmbiguous() {
        var preflightService = mock(RuntimeConfigurationDeepPreflightService.class);
        when(preflightService.check(any(), any(), any())).thenReturn(preflight());
        var codeSearch = mock(RuntimeConfigurationCodeUsageSearchService.class);
        when(codeSearch.search(any(), any())).thenReturn(new RuntimeConfigurationCodeSearchResult(
                List.of(),
                1,
                1,
                0,
                List.of()
        ));
        var operationalContext = mock(OperationalContextPort.class);
        when(operationalContext.loadContext(any()))
                .thenReturn(ambiguousCatalog());
        var service = new RuntimeConfigurationDeepContextService(
                preflightService,
                codeSearch,
                operationalContext,
                new OperationalContextOwnershipResolver()
        );

        var result = service.build(
                RuntimeConfigurationVerificationMode.DEEP,
                "runtime-config",
                "backend",
                null,
                deterministicContext("billing.endpoint")
        ).orElseThrow();

        assertEquals(OperationalContextOwnershipResolution.AMBIGUOUS, result.ownership().situationType());
        assertTrue(result.ownership().primaryOwners().stream()
                .allMatch(owner -> owner.confidence().equals("low")));
        assertTrue(result.ownership().visibilityLimits().stream()
                .anyMatch(limit -> limit.contains("no explicit owner")));
    }

    private static RuntimeConfigurationDeepPreflight preflight() {
        return new RuntimeConfigurationDeepPreflight(
                RuntimeConfigurationDeepPreflightStatus.READY,
                "runtime-config",
                "backend",
                "Backend",
                "backend",
                List.of(new RuntimeConfigurationDeepRepositoryScope(
                        "backend-scope",
                        "backend-repo",
                        "primary",
                        1,
                        "platform/backend",
                        "backend",
                        "whole-repository",
                        List.of(),
                        "release-42",
                        "release-42",
                        RuntimeConfigurationCodeRefSource.REQUESTED,
                        true,
                        false,
                        true,
                        List.of()
                )),
                List.of(),
                List.of()
        );
    }

    private static RuntimeConfigurationCodeSearchResult codeResult() {
        return new RuntimeConfigurationCodeSearchResult(
                List.of(new RuntimeConfigurationCodeGrounding(
                        "code-grounding-001",
                        "backend-scope",
                        "backend-repo",
                        "platform/backend",
                        "release-42",
                        "src/main/java/NotificationClient.java",
                        12,
                        "NotificationClient",
                        "notifications.endpoint",
                        "difference-001",
                        RuntimeConfigurationCodeUsageKind.VALUE_ANNOTATION,
                        RuntimeConfigurationGroundingConfidence.HIGH
                )),
                1,
                1,
                1,
                List.of()
        );
    }

    private static RuntimeConfigurationDeterministicContext deterministicContext(String path) {
        return new RuntimeConfigurationDeterministicContext(
                "runtime-config",
                "backend",
                "Backend",
                "backend",
                "dev1",
                "zt001",
                RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED,
                null,
                null,
                List.of(),
                List.of(),
                List.of(new RuntimeConfigurationDifference(
                        "difference-001",
                        RuntimeConfigurationFileRole.APPLICATION_YAML,
                        0,
                        path,
                        RuntimeConfigurationChangeKind.CHANGED,
                        RuntimeConfigurationValueType.STRING,
                        RuntimeConfigurationValueType.STRING,
                        RuntimeConfigurationSensitivity.NON_SENSITIVE,
                        "source-token",
                        "target-token"
                )),
                List.of()
        );
    }

    private static OperationalContextDtos.OperationalContextCatalog relatedCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map(
                        "id", "customer-contact",
                        "name", "Customer contact",
                        "participants", map(
                                "primarySystems", List.of("backend"),
                                "supportingSystems", List.of("notification")
                        ),
                        "references", map("systems", List.of("backend", "notification"))
                )),
                List.of(
                        system("backend", List.of(), "Backend team"),
                        system("notification", List.of("notifications"), "Notification team")
                ),
                List.of(map(
                        "id", "customer-notification",
                        "name", "Customer notification",
                        "participants", map(
                                "source", map("system", "backend"),
                                "targets", List.of(map("system", "notification"))
                        ),
                        "references", map("systems", List.of("backend", "notification"))
                )),
                List.of(repository(List.of("backend", "notification"))),
                List.of(scope()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );
    }

    private static OperationalContextDtos.OperationalContextCatalog ambiguousCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(
                        system("backend", List.of(), null),
                        system("billing", List.of("billing"), null)
                ),
                List.of(),
                List.of(repository(List.of("backend"))),
                List.of(scope()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );
    }

    private static Map<String, Object> system(
            String id,
            List<String> aliases,
            String ownerLabel
    ) {
        var values = map(
                "id", id,
                "name", Character.toUpperCase(id.charAt(0)) + id.substring(1),
                "kind", "internal-system",
                "aliases", aliases
        );
        if (ownerLabel != null) {
            values.put("ownership", map(
                    "ownerLabel", ownerLabel,
                    "ownershipStatus", "explicit",
                    "confidence", "high",
                    "source", "test"
            ));
        }
        return values;
    }

    private static Map<String, Object> repository(List<String> systemIds) {
        return map(
                "id", "backend-repo",
                "name", "Backend repository",
                "git", map(
                        "provider", "gitlab",
                        "group", "platform",
                        "project", "backend",
                        "projectPath", "platform/backend",
                        "defaultBranch", "main"
                ),
                "references", map("systems", systemIds)
        );
    }

    private static Map<String, Object> scope() {
        return map(
                "id", "backend-scope",
                "name", "Backend scope",
                "target", map("type", "system", "id", "backend"),
                "repositories", List.of(map(
                        "repoId", "backend-repo",
                        "role", "primary",
                        "priority", 1,
                        "searchMode", "whole-repository"
                ))
        );
    }

    private static LinkedHashMap<String, Object> map(Object... keyValues) {
        var result = new LinkedHashMap<String, Object>();
        for (var index = 0; index < keyValues.length; index += 2) {
            result.put((String) keyValues[index], keyValues[index + 1]);
        }
        return result;
    }
}
