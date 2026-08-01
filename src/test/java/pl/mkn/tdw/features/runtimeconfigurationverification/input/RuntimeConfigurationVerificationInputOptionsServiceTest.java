package pl.mkn.tdw.features.runtimeconfigurationverification.input;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationSystemOption;
import pl.mkn.tdw.features.runtimeconfigurationverification.source.RuntimeConfigurationRepositoryCatalog;
import pl.mkn.tdw.features.runtimeconfigurationverification.source.RuntimeConfigurationRepositoryProfile;
import pl.mkn.tdw.features.runtimeconfigurationverification.source.RuntimeConfigurationRepositoryProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeConfigurationVerificationInputOptionsServiceTest {

    @Test
    void shouldExposeSupportedModesBranchesRepositoriesAndSystems() {
        var repositoryCatalog = mock(RuntimeConfigurationRepositoryCatalog.class);
        when(repositoryCatalog.available()).thenReturn(List.of(
                new RuntimeConfigurationRepositoryProfile(
                        "runtime-config",
                        "Runtime configuration",
                        "hidden-connection",
                        "hidden/project"
                )
        ));
        var scopeResolver = mock(RuntimeConfigurationScopeResolver.class);
        when(scopeResolver.availableSystems()).thenReturn(List.of(
                new RuntimeConfigurationSystemOption("backend", "Backend", "backend")
        ));
        var properties = new RuntimeConfigurationRepositoryProperties();
        properties.setBranches(List.of("dev", "dev2", "uat", "uat2"));
        var service = new RuntimeConfigurationVerificationInputOptionsService(
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
