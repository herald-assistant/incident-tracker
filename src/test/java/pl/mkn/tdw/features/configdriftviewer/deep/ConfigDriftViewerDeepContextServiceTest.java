package pl.mkn.tdw.features.configdriftviewer.deep;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerCodeGrounding;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerCodeRefSource;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerCodeUsageKind;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContextStatus;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepPreflight;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepPreflightStatus;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepRepositoryScope;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerGroundingConfidence;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDifference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
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

class ConfigDriftViewerDeepContextServiceTest {

    @Test
    void shouldNotCallDeepCapabilitiesInBasicMode() {
        var preflightService = mock(ConfigDriftViewerDeepPreflightService.class);
        var codeSearch = mock(ConfigDriftViewerCodeUsageSearchService.class);
        var operationalContext = mock(OperationalContextPort.class);
        var ownership = mock(OperationalContextOwnershipResolver.class);
        var service = new ConfigDriftViewerDeepContextService(
                preflightService,
                codeSearch,
                operationalContext,
                ownership
        );

        var result = service.build(
                ConfigDriftViewerMode.BASIC,
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
        var preflightService = mock(ConfigDriftViewerDeepPreflightService.class);
        when(preflightService.check("runtime-config", "backend", "release-42"))
                .thenReturn(preflight());
        var codeSearch = mock(ConfigDriftViewerCodeUsageSearchService.class);
        when(codeSearch.search(any(), any())).thenReturn(codeResult());
        var operationalContext = mock(OperationalContextPort.class);
        when(operationalContext.loadContext(any())).thenReturn(relatedCatalog());
        var service = new ConfigDriftViewerDeepContextService(
                preflightService,
                codeSearch,
                operationalContext,
                new OperationalContextOwnershipResolver()
        );

        var result = service.build(
                ConfigDriftViewerMode.DEEP,
                "runtime-config",
                "backend",
                "release-42",
                deterministicContext("notifications.endpoint")
        ).orElseThrow();

        assertEquals(ConfigDriftViewerDeepContextStatus.PARTIAL, result.status());
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
        var preflightService = mock(ConfigDriftViewerDeepPreflightService.class);
        when(preflightService.check(any(), any(), any())).thenReturn(preflight());
        var codeSearch = mock(ConfigDriftViewerCodeUsageSearchService.class);
        when(codeSearch.search(any(), any())).thenReturn(new ConfigDriftViewerCodeSearchResult(
                List.of(),
                1,
                1,
                0,
                List.of()
        ));
        var operationalContext = mock(OperationalContextPort.class);
        when(operationalContext.loadContext(any()))
                .thenReturn(ambiguousCatalog());
        var service = new ConfigDriftViewerDeepContextService(
                preflightService,
                codeSearch,
                operationalContext,
                new OperationalContextOwnershipResolver()
        );

        var result = service.build(
                ConfigDriftViewerMode.DEEP,
                "runtime-config",
                "backend",
                null,
                deterministicContext("crm.customer-profile.endpoint")
        ).orElseThrow();

        assertEquals(OperationalContextOwnershipResolution.AMBIGUOUS, result.ownership().situationType());
        assertTrue(result.ownership().primaryOwners().stream()
                .allMatch(owner -> owner.confidence().equals("low")));
        assertTrue(result.ownership().visibilityLimits().stream()
                .anyMatch(limit -> limit.contains("no explicit owner")));
    }

    private static ConfigDriftViewerDeepPreflight preflight() {
        return new ConfigDriftViewerDeepPreflight(
                ConfigDriftViewerDeepPreflightStatus.READY,
                "runtime-config",
                "backend",
                "Backend",
                "backend",
                List.of(new ConfigDriftViewerDeepRepositoryScope(
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
                        ConfigDriftViewerCodeRefSource.REQUESTED,
                        true,
                        false,
                        true,
                        List.of()
                )),
                List.of(),
                List.of()
        );
    }

    private static ConfigDriftViewerCodeSearchResult codeResult() {
        return new ConfigDriftViewerCodeSearchResult(
                List.of(new ConfigDriftViewerCodeGrounding(
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
                        ConfigDriftViewerCodeUsageKind.VALUE_ANNOTATION,
                        ConfigDriftViewerGroundingConfidence.HIGH
                )),
                1,
                1,
                1,
                List.of()
        );
    }

    private static ConfigDriftViewerDeterministicContext deterministicContext(String path) {
        return new ConfigDriftViewerDeterministicContext(
                "runtime-config",
                "backend",
                "Backend",
                "backend",
                "dev1",
                "zt001",
                ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED,
                null,
                null,
                List.of(),
                List.of(),
                List.of(new ConfigDriftViewerDifference(
                        "difference-001",
                        ConfigDriftViewerFileRole.APPLICATION_YAML,
                        0,
                        path,
                        ConfigDriftViewerChangeKind.CHANGED,
                        ConfigDriftViewerValueType.STRING,
                        ConfigDriftViewerValueType.STRING,
                        ConfigDriftViewerSensitivity.NON_SENSITIVE,
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
                        system("customer-profile", List.of("customer-profile"), null)
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
                "kind", "internal-service",
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
