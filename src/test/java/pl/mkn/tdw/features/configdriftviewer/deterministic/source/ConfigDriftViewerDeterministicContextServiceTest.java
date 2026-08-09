package pl.mkn.tdw.features.configdriftviewer.deterministic.source;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.configdriftviewer.deterministic.engine
        .ConfigDriftViewerDeterministicEngine;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationFile;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationSnapshot;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ConfigDriftViewerVarParser;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ConfigDriftViewerYamlParser;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjection;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjectionBuilder;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScope;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigDriftViewerDeterministicContextServiceTest {

    @Test
    void shouldBuildSanitizedContextAndOperatorProjectionFromTheSameLoadedSnapshots() {
        var sourceLoader = mock(ConfigDriftViewerSourceLoader.class);
        var yamlParser = mock(ConfigDriftViewerYamlParser.class);
        var varParser = mock(ConfigDriftViewerVarParser.class);
        var deterministicEngine = mock(ConfigDriftViewerDeterministicEngine.class);
        var projectionBuilder = mock(ConfigDriftViewerDiffProjectionBuilder.class);
        var listener = mock(ConfigDriftViewerDeterministicContextListener.class);
        var service = new ConfigDriftViewerDeterministicContextService(
                sourceLoader,
                yamlParser,
                varParser,
                deterministicEngine,
                projectionBuilder
        );
        var scope = new ConfigDriftViewerScope(
                "runtime-config",
                "config-gitlab",
                "platform/runtime-config",
                "crm-backend",
                "CRM Backend",
                "backend"
        );
        var sourceCoverage = new ConfigDriftViewerBranchCoverage("dev1", true, List.of());
        var targetCoverage = new ConfigDriftViewerBranchCoverage("zt001", true, List.of());
        var sourceRaw = new ConfigDriftViewerRawSnapshot(
                "dev1",
                sourceCoverage,
                Map.of(
                        ConfigDriftViewerFileRole.APPLICATION_YAML,
                        new ConfigDriftViewerRawFile(
                                ConfigDriftViewerFileRole.APPLICATION_YAML,
                                "backend/application.yml.kv",
                                "source-yaml",
                                null
                        ),
                        ConfigDriftViewerFileRole.LOCAL_VAR,
                        new ConfigDriftViewerRawFile(
                                ConfigDriftViewerFileRole.LOCAL_VAR,
                                "backend/local.var",
                                "source-var",
                                null
                        )
                )
        );
        var targetRaw = new ConfigDriftViewerRawSnapshot(
                "zt001",
                targetCoverage,
                Map.of(
                        ConfigDriftViewerFileRole.APPLICATION_YAML,
                        new ConfigDriftViewerRawFile(
                                ConfigDriftViewerFileRole.APPLICATION_YAML,
                                "backend/application.yml.kv",
                                "target-yaml",
                                null
                        ),
                        ConfigDriftViewerFileRole.LOCAL_VAR,
                        new ConfigDriftViewerRawFile(
                                ConfigDriftViewerFileRole.LOCAL_VAR,
                                "backend/local.var",
                                "target-var",
                                null
                        )
                )
        );
        var sourceYaml = parsed(
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                "backend/application.yml.kv"
        );
        var targetYaml = parsed(
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                "backend/application.yml.kv"
        );
        var sourceVar = parsed(ConfigDriftViewerFileRole.LOCAL_VAR, "backend/local.var");
        var targetVar = parsed(ConfigDriftViewerFileRole.LOCAL_VAR, "backend/local.var");
        var deterministic = mock(ConfigDriftViewerDeterministicContext.class);
        var projection = new ConfigDriftViewerDiffProjection("dev1", "zt001", List.of());

        when(sourceLoader.load(scope, "dev1", "zt001"))
                .thenReturn(new ConfigDriftViewerRawSnapshotPair(sourceRaw, targetRaw));
        when(yamlParser.parse("backend/application.yml.kv", "source-yaml")).thenReturn(sourceYaml);
        when(yamlParser.parse("backend/application.yml.kv", "target-yaml")).thenReturn(targetYaml);
        when(varParser.parse(
                ConfigDriftViewerFileRole.LOCAL_VAR,
                "backend/local.var",
                "source-var"
        )).thenReturn(sourceVar);
        when(varParser.parse(
                ConfigDriftViewerFileRole.LOCAL_VAR,
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

    private ParsedConfigurationFile parsed(ConfigDriftViewerFileRole role, String path) {
        return new ParsedConfigurationFile(role, path, List.of(), List.of());
    }
}
