package pl.mkn.tdw.features.runtimeconfigurationverification.deep;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeRefSource;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflightStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeException;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGit;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextOwnership;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextReferences;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchTarget;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuntimeConfigurationDeepPreflightServiceTest {

    @Test
    void shouldReturnReadyPreflightForConfirmedRequestedRefWithoutExposingCredentials() throws Exception {
        var fixture = fixture(catalog(true));
        when(fixture.gitLabPort.branchExists("platform", "backend", "release-42"))
                .thenReturn(true);

        var result = fixture.service.check("runtime-config", "backend", "release-42");

        assertEquals(RuntimeConfigurationDeepPreflightStatus.READY, result.status());
        assertEquals("backend", result.resolvedConfigurationDirectory());
        assertEquals("release-42", result.repositories().get(0).usedRef());
        assertEquals(RuntimeConfigurationCodeRefSource.REQUESTED, result.repositories().get(0).refSource());
        assertFalse(result.repositories().get(0).deploymentRefConfirmed());
        var json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("secret-code-token"));
        assertFalse(json.contains("https://code-gitlab.invalid"));
    }

    @Test
    void shouldFallbackToConfirmedDefaultRefAndExposeDeploymentVisibilityLimit() {
        var fixture = fixture(catalog(true));
        when(fixture.gitLabPort.branchExists("platform", "backend", "missing-ref"))
                .thenReturn(false);
        when(fixture.gitLabPort.branchExists("platform", "backend", "main"))
                .thenReturn(true);

        var result = fixture.service.check("runtime-config", "backend", "missing-ref");

        assertTrue(result.ready());
        assertEquals("main", result.repositories().get(0).usedRef());
        assertEquals(RuntimeConfigurationCodeRefSource.DEFAULT_BRANCH, result.repositories().get(0).refSource());
        assertTrue(result.visibilityLimits().stream()
                .anyMatch(limit -> limit.contains("not evidence of the deployed version")));
    }

    @Test
    void shouldBlockWhenOperationalContextIsDisabledWithoutCallingDependencies() {
        var properties = new OperationalContextProperties();
        properties.setEnabled(false);
        var contextPort = mock(OperationalContextPort.class);
        var scopeResolver = mock(RuntimeConfigurationScopeResolver.class);
        var gitLabPort = mock(GitLabRepositoryPort.class);
        var service = new RuntimeConfigurationDeepPreflightService(
                properties,
                contextPort,
                scopeResolver,
                gitLabProperties(),
                gitLabPort
        );

        var result = service.check("runtime-config", "backend", null);

        assertEquals(RuntimeConfigurationDeepPreflightStatus.BLOCKED, result.status());
        assertEquals("OPERATIONAL_CONTEXT_DISABLED", result.blockers().get(0).code());
        verifyNoInteractions(contextPort, scopeResolver, gitLabPort);
    }

    @Test
    void shouldBlockUnknownNonInternalMissingAndAmbiguousConfigurationSystems() {
        var properties = enabledProperties();
        var contextPort = mock(OperationalContextPort.class);
        var scopeResolver = mock(RuntimeConfigurationScopeResolver.class);
        when(scopeResolver.resolve(anyString(), anyString()))
                .thenThrow(RuntimeConfigurationScopeException.systemNotFound("unknown"))
                .thenThrow(RuntimeConfigurationScopeException.systemNotInternal("external"))
                .thenThrow(RuntimeConfigurationScopeException.configurationDirectoryMissing("missing"))
                .thenThrow(RuntimeConfigurationScopeException.configurationDirectoryAmbiguous("ambiguous"));
        var service = new RuntimeConfigurationDeepPreflightService(
                properties,
                contextPort,
                scopeResolver,
                gitLabProperties(),
                mock(GitLabRepositoryPort.class)
        );

        assertEquals(
                "RUNTIME_CONFIGURATION_SYSTEM_NOT_FOUND",
                service.check("runtime-config", "unknown", null).blockers().get(0).code()
        );
        assertEquals(
                "RUNTIME_CONFIGURATION_SYSTEM_NOT_INTERNAL",
                service.check("runtime-config", "external", null).blockers().get(0).code()
        );
        assertEquals(
                "RUNTIME_CONFIGURATION_DIRECTORY_MISSING",
                service.check("runtime-config", "missing", null).blockers().get(0).code()
        );
        assertEquals(
                "RUNTIME_CONFIGURATION_DIRECTORY_AMBIGUOUS",
                service.check("runtime-config", "ambiguous", null).blockers().get(0).code()
        );
        verifyNoInteractions(contextPort);
    }

    @Test
    void shouldBlockEmptyCatalogMissingScopeAndUnavailableOperationalContext() {
        var fixture = fixture(catalog(false));

        var missingScope = fixture.service.check("runtime-config", "backend", null);

        assertEquals(RuntimeConfigurationDeepPreflightStatus.BLOCKED, missingScope.status());
        assertTrue(missingScope.blockers().stream()
                .anyMatch(blocker -> blocker.code().equals("DEEP_CODE_SEARCH_SCOPE_MISSING")));
        verify(fixture.gitLabPort, never()).branchExists(anyString(), anyString(), anyString());

        when(fixture.contextPort.loadContext(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("unsafe upstream details"));
        var unavailable = fixture.service.check("runtime-config", "backend", null);
        assertTrue(unavailable.blockers().stream()
                .anyMatch(blocker -> blocker.code().equals("OPERATIONAL_CONTEXT_UNAVAILABLE")));
        assertFalse(unavailable.blockers().stream()
                .anyMatch(blocker -> blocker.message().contains("unsafe upstream details")));

        var emptyFixture = fixture(OperationalContextCatalog.empty());
        var empty = emptyFixture.service.check("runtime-config", "backend", null);
        assertTrue(empty.blockers().stream()
                .anyMatch(blocker -> blocker.code().equals("DEEP_CODE_SEARCH_SCOPE_MISSING")));
    }

    @Test
    void shouldRejectUnsafePreflightInputBeforeCallingDependencies() {
        var fixture = fixture(catalog(true));

        var result = fixture.service.check("runtime-config", "backend", "../unsafe");

        assertEquals("DEEP_PREFLIGHT_INPUT_INVALID", result.blockers().get(0).code());
        verifyNoInteractions(fixture.contextPort, fixture.gitLabPort);
    }

    private static Fixture fixture(OperationalContextCatalog catalog) {
        var properties = enabledProperties();
        var contextPort = mock(OperationalContextPort.class);
        when(contextPort.loadContext(org.mockito.ArgumentMatchers.any())).thenReturn(catalog);
        var scopeResolver = mock(RuntimeConfigurationScopeResolver.class);
        when(scopeResolver.resolve("runtime-config", "backend")).thenReturn(new RuntimeConfigurationScope(
                "runtime-config",
                "hidden-config-connection",
                "hidden/config-project",
                "backend",
                "Backend",
                "backend"
        ));
        var gitLabPort = mock(GitLabRepositoryPort.class);
        return new Fixture(
                new RuntimeConfigurationDeepPreflightService(
                        properties,
                        contextPort,
                        scopeResolver,
                        gitLabProperties(),
                        gitLabPort
                ),
                contextPort,
                gitLabPort
        );
    }

    private static OperationalContextProperties enabledProperties() {
        var properties = new OperationalContextProperties();
        properties.setEnabled(true);
        return properties;
    }

    private static GitLabProperties gitLabProperties() {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://code-gitlab.invalid");
        properties.setGroup("platform");
        properties.setToken("secret-code-token");
        return properties;
    }

    private static OperationalContextCatalog catalog(boolean includeScope) {
        return new OperationalContextCatalog(
                List.of(),
                List.of(),
                List.of(system("backend")),
                List.of(),
                List.of(repository()),
                includeScope ? List.of(scope()) : List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );
    }

    private static OperationalContextSystem system(String id) {
        return new OperationalContextSystem(
                id,
                "Backend",
                "Backend",
                "internal-system",
                "active",
                "available",
                "high",
                "Backend system",
                "Runs backend capabilities",
                List.of(),
                List.of(),
                null,
                new OperationalContextOwnership(
                        List.of(),
                        "Backend team",
                        "explicit",
                        "high",
                        "systems.yml",
                        List.of()
                ),
                OperationalContextReferences.empty(),
                null,
                List.of(),
                Map.of()
        );
    }

    private static OperationalContextRepository repository() {
        return new OperationalContextRepository(
                "backend-repo",
                "Backend repository",
                "backend",
                "application",
                "active",
                "high",
                "Backend code",
                "Implements backend",
                List.of(),
                List.of(),
                new OperationalContextGit(
                        "gitlab",
                        "platform",
                        "backend",
                        "platform/backend",
                        "main",
                        null,
                        List.of(),
                        false
                ),
                new OperationalContextReferences(
                        List.of("backend"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                null,
                List.of(),
                Map.of()
        );
    }

    private static OperationalContextRepositorySearchScope scope() {
        return new OperationalContextRepositorySearchScope(
                "backend-scope",
                "Backend",
                "system",
                "active",
                "Backend source scope",
                new OperationalContextRepositorySearchTarget("system", "backend"),
                List.of("runtime configuration verification"),
                List.of(new OperationalContextRepositorySearchRepository(
                        "backend-repo",
                        "primary",
                        1,
                        "Backend implementation",
                        List.of("configuration usage"),
                        "whole-repository",
                        List.of()
                )),
                List.of(),
                Map.of()
        );
    }

    private record Fixture(
            RuntimeConfigurationDeepPreflightService service,
            OperationalContextPort contextPort,
            GitLabRepositoryPort gitLabPort
    ) {
    }
}
