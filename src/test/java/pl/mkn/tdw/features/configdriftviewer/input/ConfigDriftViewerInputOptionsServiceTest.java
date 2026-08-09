package pl.mkn.tdw.features.configdriftviewer.input;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScopeResolver;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerSystemOption;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryCatalog;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryProfile;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigDriftViewerInputOptionsServiceTest {

    @Test
    void shouldExposeSupportedModesBranchesRepositoriesAndSystems() {
        var repositoryCatalog = mock(ConfigDriftViewerRepositoryCatalog.class);
        when(repositoryCatalog.available()).thenReturn(List.of(
                new ConfigDriftViewerRepositoryProfile(
                        "runtime-config",
                        "Runtime configuration",
                        "hidden-connection",
                        "hidden/project"
                )
        ));
        var scopeResolver = mock(ConfigDriftViewerScopeResolver.class);
        when(scopeResolver.availableSystems()).thenReturn(List.of(
                new ConfigDriftViewerSystemOption("backend", "Backend", "backend")
        ));
        var properties = new ConfigDriftViewerRepositoryProperties();
        properties.setBranches(List.of("dev", "dev2", "uat", "uat2"));
        var service = new ConfigDriftViewerInputOptionsService(
                repositoryCatalog,
                scopeResolver,
                properties
        );

        var options = service.getOptions();

        assertEquals(List.of("BASIC", "DEEP"), options.modes().stream().map(Enum::name).toList());
        assertEquals(List.of("dev", "dev2", "uat", "uat2"), options.branches());
        assertEquals("runtime-config", options.repositories().get(0).id());
        assertEquals("backend", options.systems().get(0).id());
    }
}
