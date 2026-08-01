package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.engine
        .RuntimeConfigurationDeterministicEngine;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationFile;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.RuntimeConfigurationVarParser;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.RuntimeConfigurationYamlParser;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffProjection;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffProjectionBuilder;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeConfigurationDeterministicContextServiceTest {

    @Test
    void shouldBuildSanitizedContextAndOperatorProjectionFromTheSameLoadedSnapshots() {
        var sourceLoader = mock(RuntimeConfigurationSourceLoader.class);
        var yamlParser = mock(RuntimeConfigurationYamlParser.class);
        var varParser = mock(RuntimeConfigurationVarParser.class);
        var deterministicEngine = mock(RuntimeConfigurationDeterministicEngine.class);
        var projectionBuilder = mock(RuntimeConfigurationDiffProjectionBuilder.class);
        var listener = mock(RuntimeConfigurationDeterministicContextListener.class);
        var service = new RuntimeConfigurationDeterministicContextService(
                sourceLoader,
                yamlParser,
                varParser,
                deterministicEngine,
                projectionBuilder
        );
        var scope = new RuntimeConfigurationScope(
                "runtime-config",
                "config-gitlab",
                "platform/runtime-config",
                "crm-backend",
                "CRM Backend",
                "backend"
        );
        var sourceCoverage = new RuntimeConfigurationBranchCoverage("dev1", true, List.of());
        var targetCoverage = new RuntimeConfigurationBranchCoverage("zt001", true, List.of());
        var sourceRaw = new RuntimeConfigurationRawSnapshot(
                "dev1",
                sourceCoverage,
                Map.of(
                        RuntimeConfigurationFileRole.APPLICATION_YAML,
                        new RuntimeConfigurationRawFile(
                                RuntimeConfigurationFileRole.APPLICATION_YAML,
                                "backend/application.yml.kv",
                                "source-yaml",
                                null
                        ),
                        RuntimeConfigurationFileRole.LOCAL_VAR,
                        new RuntimeConfigurationRawFile(
                                RuntimeConfigurationFileRole.LOCAL_VAR,
                                "backend/local.var",
                                "source-var",
                                null
                        )
                )
        );
        var targetRaw = new RuntimeConfigurationRawSnapshot(
                "zt001",
                targetCoverage,
                Map.of(
                        RuntimeConfigurationFileRole.APPLICATION_YAML,
                        new RuntimeConfigurationRawFile(
                                RuntimeConfigurationFileRole.APPLICATION_YAML,
                                "backend/application.yml.kv",
                                "target-yaml",
                                null
                        ),
                        RuntimeConfigurationFileRole.LOCAL_VAR,
                        new RuntimeConfigurationRawFile(
                                RuntimeConfigurationFileRole.LOCAL_VAR,
                                "backend/local.var",
                                "target-var",
                                null
                        )
                )
        );
        var sourceYaml = parsed(
                RuntimeConfigurationFileRole.APPLICATION_YAML,
                "backend/application.yml.kv"
        );
        var targetYaml = parsed(
                RuntimeConfigurationFileRole.APPLICATION_YAML,
                "backend/application.yml.kv"
        );
        var sourceVar = parsed(RuntimeConfigurationFileRole.LOCAL_VAR, "backend/local.var");
        var targetVar = parsed(RuntimeConfigurationFileRole.LOCAL_VAR, "backend/local.var");
        var deterministic = mock(RuntimeConfigurationDeterministicContext.class);
        var projection = new RuntimeConfigurationDiffProjection("dev1", "zt001", List.of());

        when(sourceLoader.load(scope, "dev1", "zt001"))
                .thenReturn(new RuntimeConfigurationRawSnapshotPair(sourceRaw, targetRaw));
        when(yamlParser.parse("backend/application.yml.kv", "source-yaml")).thenReturn(sourceYaml);
        when(yamlParser.parse("backend/application.yml.kv", "target-yaml")).thenReturn(targetYaml);
        when(varParser.parse(
                RuntimeConfigurationFileRole.LOCAL_VAR,
                "backend/local.var",
                "source-var"
        )).thenReturn(sourceVar);
        when(varParser.parse(
                RuntimeConfigurationFileRole.LOCAL_VAR,
                "backend/local.var",
                "target-var"
        )).thenReturn(targetVar);

        when(deterministicEngine.build(
                same(scope),
                same(sourceCoverage),
                same(targetCoverage),
                any(ParsedConfigurationSnapshot.class),
                any(ParsedConfigurationSnapshot.class)
        )).thenReturn(deterministic);
        when(projectionBuilder.build(
                any(ParsedConfigurationSnapshot.class),
                any(ParsedConfigurationSnapshot.class),
                same(deterministic)
        )).thenReturn(projection);

        var result = service.build(scope, "dev1", "zt001", listener);

        assertSame(deterministic, result.context());
        assertSame(projection, result.configurationDiff());
        verify(sourceLoader).load(scope, "dev1", "zt001");
        var engineSource = ArgumentCaptor.forClass(ParsedConfigurationSnapshot.class);
        var engineTarget = ArgumentCaptor.forClass(ParsedConfigurationSnapshot.class);
        verify(deterministicEngine).build(
                same(scope),
                same(sourceCoverage),
                same(targetCoverage),
                engineSource.capture(),
                engineTarget.capture()
        );
        var projectionSource = ArgumentCaptor.forClass(ParsedConfigurationSnapshot.class);
        var projectionTarget = ArgumentCaptor.forClass(ParsedConfigurationSnapshot.class);
        verify(projectionBuilder).build(
                projectionSource.capture(),
                projectionTarget.capture(),
                same(deterministic)
        );
        assertSame(engineSource.getValue(), projectionSource.getValue());
        assertSame(engineTarget.getValue(), projectionTarget.getValue());
        var order = inOrder(listener);
        order.verify(listener).onSourceStarted();
        order.verify(listener).onSourceCompleted();
        order.verify(listener).onParseStarted();
        order.verify(listener).onParseCompleted();
        order.verify(listener).onDiffStarted();
        order.verify(listener).onDiffCompleted(same(result));
    }

    private ParsedConfigurationFile parsed(RuntimeConfigurationFileRole role, String path) {
        return new ParsedConfigurationFile(role, path, List.of(), List.of());
    }
}
