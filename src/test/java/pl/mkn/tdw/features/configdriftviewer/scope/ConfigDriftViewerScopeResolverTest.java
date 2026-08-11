package pl.mkn.tdw.features.configdriftviewer.scope;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryCatalog;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryProfile;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextReadSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigDriftViewerScopeResolverTest {

    @Test
    void shouldResolveRepositoryAndSafeDirectoryFromInternalSystem() {
        var resolver = resolver(List.of(system(
                "crm-customer-service",
                "internal-service",
                Map.of("runtime", Map.of("configurationDirectory", "crm/customer-service"))
        )));

        var scope = resolver.resolve("crm-runtime-config", "crm-customer-service");

        assertEquals("crm-runtime-config", scope.repositoryId());
        assertEquals("crm-config-connection", scope.connectionId());
        assertEquals("crm/runtime-config", scope.projectPath());
        assertEquals("crm-customer-service", scope.systemId());
        assertEquals("crm/customer-service", scope.configurationDirectory());
    }

    @Test
    void shouldResolveExactConfigurationDirectorySignal() {
        var resolver = resolver(List.of(system(
                "crm-customer-service",
                "internal-service",
                Map.of(
                        "matchSignals",
                        Map.of("exact", Map.of("configurationDirectories", List.of("crm/customer-service")))
                )
        )));

        assertEquals(
                "crm/customer-service",
                resolver.resolve("crm-runtime-config", "crm-customer-service").configurationDirectory()
        );
    }

    @Test
    void shouldRejectMissingAmbiguousAndUnsafeDirectorySignals() {
        assertEquals(
                "RUNTIME_CONFIGURATION_DIRECTORY_MISSING",
                assertThrows(
                        ConfigDriftViewerScopeException.class,
                        () -> resolver(List.of(system("crm-missing", "internal-service", Map.of())))
                                .resolve("crm-runtime-config", "crm-missing")
                ).code()
        );
        assertEquals(
                "RUNTIME_CONFIGURATION_DIRECTORY_AMBIGUOUS",
                assertThrows(
                        ConfigDriftViewerScopeException.class,
                        () -> resolver(List.of(system(
                                "crm-ambiguous",
                                "internal-service",
                                Map.of(
                                        "runtime", Map.of("configurationDirectory", "crm/customer-service"),
                                        "deployment", Map.of("configurationDirectory", "crm/contact-worker")
                                )
                        ))).resolve("crm-runtime-config", "crm-ambiguous")
                ).code()
        );
        assertEquals(
                "RUNTIME_CONFIGURATION_DIRECTORY_INVALID",
                assertThrows(
                        ConfigDriftViewerScopeException.class,
                        () -> resolver(List.of(system(
                                "crm-unsafe",
                                "internal-service",
                                Map.of("runtime", Map.of("configurationDirectory", "../crm"))
                        ))).resolve("crm-runtime-config", "crm-unsafe")
                ).code()
        );
    }

    @Test
    void shouldRejectUnknownAndNonInternalSystems() {
        var resolver = resolver(List.of(system(
                "crm-partner",
                "external-system",
                Map.of("runtime", Map.of("configurationDirectory", "crm/partner"))
        )));

        assertEquals(
                "RUNTIME_CONFIGURATION_SYSTEM_NOT_FOUND",
                assertThrows(
                        ConfigDriftViewerScopeException.class,
                        () -> resolver.resolve("crm-runtime-config", "crm-unknown")
                ).code()
        );
        assertEquals(
                "RUNTIME_CONFIGURATION_SYSTEM_NOT_INTERNAL_SERVICE",
                assertThrows(
                        ConfigDriftViewerScopeException.class,
                        () -> resolver.resolve("crm-runtime-config", "crm-partner")
                ).code()
        );
    }

    @Test
    void shouldExposeOnlyInternalSystemsWithUnambiguousSafeDirectories() {
        var resolver = resolver(List.of(
                system(
                        "crm-ready",
                        "internal-service",
                        Map.of("runtime", Map.of("configurationDirectory", "crm/customer-service"))
                ),
                system("crm-missing", "internal-service", Map.of()),
                system(
                        "crm-external",
                        "external-system",
                        Map.of("runtime", Map.of("configurationDirectory", "crm/external"))
                )
        ));

        var options = resolver.availableSystems();

        assertEquals(1, options.size());
        assertEquals("crm-ready", options.get(0).id());
        assertEquals("crm/customer-service", options.get(0).configurationDirectory());
    }

    private static ConfigDriftViewerScopeResolver resolver(
            List<OperationalContextDtos.OperationalContextSystem> systems
    ) {
        var repositoryCatalog = mock(ConfigDriftViewerRepositoryCatalog.class);
        when(repositoryCatalog.require("crm-runtime-config")).thenReturn(
                new ConfigDriftViewerRepositoryProfile(
                        "crm-runtime-config",
                        "CRM runtime configuration",
                        "crm-config-connection",
                        "crm/runtime-config"
                )
        );
        var operationalContextPort = mock(OperationalContextPort.class);
        var readSession = mock(OperationalContextReadSession.class);
        when(readSession.query(any())).thenReturn(catalog(systems));
        when(operationalContextPort.capture()).thenReturn(readSession);
        return new ConfigDriftViewerScopeResolver(repositoryCatalog, operationalContextPort);
    }

    private static OperationalContextDtos.OperationalContextSystem system(
            String id,
            String kind,
            Map<String, Object> additional
    ) {
        var source = new java.util.LinkedHashMap<String, Object>();
        source.put("id", id);
        source.put("name", id + " label");
        source.put("kind", kind);
        source.putAll(additional);
        return OperationalContextDtos.system(source);
    }

    private static OperationalContextCatalog catalog(
            List<OperationalContextDtos.OperationalContextSystem> systems
    ) {
        return new OperationalContextCatalog(
                List.of(),
                List.of(),
                systems,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );
    }
}
