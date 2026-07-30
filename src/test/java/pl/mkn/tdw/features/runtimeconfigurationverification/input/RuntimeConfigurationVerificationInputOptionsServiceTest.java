package pl.mkn.tdw.features.runtimeconfigurationverification.input;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationSystemOption;
import pl.mkn.tdw.features.runtimeconfigurationverification.source.RuntimeConfigurationRepositoryCatalog;
import pl.mkn.tdw.features.runtimeconfigurationverification.source.RuntimeConfigurationRepositoryProfile;

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
        var service = new RuntimeConfigurationVerificationInputOptionsService(
                repositoryCatalog,
                scopeResolver
        );

        var options = service.getOptions();

        assertEquals(List.of("BASIC", "DEEP"), options.modes().stream().map(Enum::name).toList());
        assertEquals(20, options.branches().size());
        assertEquals("dev0", options.branches().get(0));
        assertEquals("zt009", options.branches().get(19));
        assertEquals("runtime-config", options.repositories().get(0).id());
        assertEquals("backend", options.systems().get(0).id());
    }
}
