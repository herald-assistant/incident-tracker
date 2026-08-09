package pl.mkn.tdw.features.configdriftviewer.scope;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryCatalog;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryProfile;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

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
                "backend-system",
                "internal-service",
                Map.of("runtime", Map.of("configurationDirectory", "services/backend"))
        )));

        var scope = resolver.resolve("runtime-config", "backend-system");

        assertEquals("runtime-config", scope.repositoryId());
        assertEquals("config-gitlab", scope.connectionId());
        assertEquals("platform/runtime-config", scope.projectPath());
        assertEquals("backend-system", scope.systemId());
        assertEquals("services/backend", scope.configurationDirectory());
    }

    @Test
    void shouldResolveExactConfigurationDirectorySignal() {
        var resolver = resolver(List.of(system(
                "backend-system",
                "internal-service",
                Map.of(
                        "matchSignals",
                        Map.of("exact", Map.of("configurationDirectories", List.of("backend")))
                )
        )));

        assertEquals(
                "backend",
                resolver.resolve("runtime-config", "backend-system").configurationDirectory()
        );
    }

    @Test
    void shouldRejectMissingAmbiguousAndUnsafeDirectorySignals() {
        assertEquals(
                "RUNTIME_CONFIGURATION_DIRECTORY_MISSING",
                assertThrows(
                        ConfigDriftViewerScopeException.class,
                        () -> resolver(List.of(system("missing", "internal-service", Map.of())))
                                .resolve("runtime-config", "missing")
                ).code()
        );
        assertEquals(
                "RUNTIME_CONFIGURATION_DIRECTORY_AMBIGUOUS",
                assertThrows(
                        ConfigDriftViewerScopeException.class,
                        () -> resolver(List.of(system(
                                "ambiguous",
                                "internal-service",
                                Map.of(
                                        "runtime", Map.of("configurationDirectory", "backend"),
                                        "deployment", Map.of("configurationDirectory", "worker")
                                )
                        ))).resolve("runtime-config", "ambiguous")
                ).code()
        );
        assertEquals(
                "RUNTIME_CONFIGURATION_DIRECTORY_INVALID",
                assertThrows(
                        ConfigDriftViewerScopeException.class,
                        () -> resolver(List.of(system(
                                "unsafe",
                                "internal-service",
                                Map.of("runtime", Map.of("configurationDirectory", "../backend"))
                        ))).resolve("runtime-config", "unsafe")
                ).code()
        );
    }

    @Test
    void shouldRejectUnknownAndNonInternalSystems() {
        var resolver = resolver(List.of(system(
                "partner",
                "external-system",
                Map.of("runtime", Map.of("configurationDirectory", "partner"))
        )));

        assertEquals(
                "RUNTIME_CONFIGURATION_SYSTEM_NOT_FOUND",
                assertThrows(
                        ConfigDriftViewerScopeException.class,
                        () -> resolver.resolve("runtime-config", "unknown")
                ).code()
        );
        assertEquals(
                "RUNTIME_CONFIGURATION_SYSTEM_NOT_INTERNAL_SERVICE",
                assertThrows(
                        ConfigDriftViewerScopeException.class,
                        () -> resolver.resolve("runtime-config", "partner")
                ).code()
        );
    }

    @Test
    void shouldExposeOnlyInternalSystemsWithUnambiguousSafeDirectories() {
        var resolver = resolver(List.of(
                system(
                        "ready",
                        "internal-service",
                        Map.of("runtime", Map.of("configurationDirectory", "backend"))
                ),
                system("missing", "internal-service", Map.of()),
                system(
                        "external",
                        "external-system",
                        Map.of("runtime", Map.of("configurationDirectory", "external"))
                )
        ));

        var options = resolver.availableSystems();

        assertEquals(1, options.size());
        assertEquals("ready", options.get(0).id());
        assertEquals("backend", options.get(0).configurationDirectory());
    }

    private static ConfigDriftViewerScopeResolver resolver(
            List<OperationalContextDtos.OperationalContextSystem> systems
    ) {
        var repositoryCatalog = mock(ConfigDriftViewerRepositoryCatalog.class);
        when(repositoryCatalog.require("runtime-config")).thenReturn(
                new ConfigDriftViewerRepositoryProfile(
                        "runtime-config",
                        "Runtime configuration",
                        "config-gitlab",
                        "platform/runtime-config"
                )
        );
        var operationalContextPort = mock(OperationalContextPort.class);
        when(operationalContextPort.loadContext(any())).thenReturn(catalog(systems));
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
