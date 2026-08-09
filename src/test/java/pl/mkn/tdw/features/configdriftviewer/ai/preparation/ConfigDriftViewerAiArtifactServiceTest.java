package pl.mkn.tdw.features.configdriftviewer.ai.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import pl.mkn.tdw.features.configdriftviewer.ai.ConfigDriftViewerAiTestFixtures;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerDifference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .SanitizedConfigurationDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .SanitizedConfigurationNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source
        .ConfigDriftViewerFileRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDriftViewerAiArtifactServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConfigDriftViewerAiArtifactService service =
            new ConfigDriftViewerAiArtifactService(objectMapper);

    @Test
    void shouldRenderCompactV1TreeWithAllParametersAndWithoutSecretsOrLegacyManifests()
            throws Exception {
        var result = service.render(
                ConfigDriftViewerAiTestFixtures.deterministic(
                        pl.mkn.tdw.features.configdriftviewer.deterministic.model
                                .ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
                ),
                null
        );

        assertThat(result.contents().keySet()).containsExactly(
                "runtime-configuration/scope.json",
                "runtime-configuration/coverage.json",
                "config-drift-viewer/configuration-tree.yaml",
                "runtime-configuration/changes.json",
                "config-drift-viewer/response-contract.json"
        );
        assertThat(result.contents().keySet())
                .noneMatch(name -> name.contains("/manifest/") || name.contains("manifest-index"));
        assertThat(objectMapper.readTree(result.contents().get("runtime-configuration/scope.json"))
                .get("mode").asText()).isEqualTo("DEEP");

        var tree = result.contents().get("config-drift-viewer/configuration-tree.yaml");
        var changes = result.contents().get("runtime-configuration/changes.json");
        var all = String.join("\n", result.contents().values());

        assertThat(tree)
                .contains(
                        "formatVersion: 1",
                        "relationCodes:",
                        "typeCodes:",
                        "\"timeout\"",
                        "\"password\"",
                        "\"p:value-1\"",
                        "M: sensitive scalar suppressed before AI"
                );
        assertThat((Object) new Yaml().load(tree)).isInstanceOf(Map.class);
        assertThat(objectMapper.readTree(changes).get("formatVersion").asInt()).isEqualTo(1);
        assertThat(changes)
                .contains(
                        "\"differenceColumns\"",
                        "\"findingColumns\"",
                        "\"difference-1\"",
                        "\"finding-1\""
                );
        assertThat(all)
                .doesNotContain(
                        "raw-secret-source",
                        "raw-secret-target",
                        "raw-difference-secret-source",
                        "raw-difference-secret-target"
                )
                .doesNotContainIgnoringCase("hmac")
                .doesNotContainIgnoringCase("sha256")
                .doesNotContainIgnoringCase("\"hash\"");
        assertThat(result.contents()).doesNotContainKey("runtime-configuration/deep-context.json");
    }

    @Test
    void shouldPreserveMissingSideTypesForAddedAndRemovedDifferences() throws Exception {
        var baseline = ConfigDriftViewerAiTestFixtures.deterministic(
                pl.mkn.tdw.features.configdriftviewer.deterministic.model
                        .ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
        );
        var added = new ConfigDriftViewerDifference(
                "difference-added",
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                0,
                "feature.new-setting",
                ConfigDriftViewerChangeKind.ADDED,
                null,
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerSensitivity.NON_SENSITIVE,
                null,
                "target-token"
        );
        var removed = new ConfigDriftViewerDifference(
                "difference-removed",
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                0,
                "feature.old-setting",
                ConfigDriftViewerChangeKind.REMOVED,
                ConfigDriftViewerValueType.STRING,
                null,
                ConfigDriftViewerSensitivity.NON_SENSITIVE,
                "source-token",
                null
        );
        var context = new pl.mkn.tdw.features.configdriftviewer.deterministic.model
                .ConfigDriftViewerDeterministicContext(
                baseline.repositoryId(),
                baseline.systemId(),
                baseline.systemLabel(),
                baseline.configurationDirectory(),
                baseline.sourceBranch(),
                baseline.targetBranch(),
                baseline.status(),
                baseline.sourceCoverage(),
                baseline.targetCoverage(),
                baseline.documents(),
                baseline.references(),
                List.of(added, removed),
                baseline.findings()
        );

        var result = service.render(context, null);
        var rows = objectMapper.readTree(
                result.contents().get("runtime-configuration/changes.json")
        ).get("differences");

        assertThat(rows.get(0).get(4).asText()).isEqualTo("A");
        assertThat(rows.get(0).get(5).isNull()).isTrue();
        assertThat(rows.get(0).get(6).asText()).isEqualTo("S");
        assertThat(rows.get(0).get(8).asText()).isEqualTo("A");
        assertThat(rows.get(0).get(9).asText()).isEqualTo("p:target-token");
        assertThat(rows.get(1).get(4).asText()).isEqualTo("R");
        assertThat(rows.get(1).get(5).asText()).isEqualTo("S");
        assertThat(rows.get(1).get(6).isNull()).isTrue();
        assertThat(rows.get(1).get(8).asText()).isEqualTo("p:source-token");
        assertThat(rows.get(1).get(9).asText()).isEqualTo("A");
    }

    @Test
    void shouldKeepTruncatedTreeValidAndLeaveDeterministicResultUnchanged() {
        var baseline = ConfigDriftViewerAiTestFixtures.deterministic(
                pl.mkn.tdw.features.configdriftviewer.deterministic.model
                        .ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
        );
        var children = new ArrayList<SanitizedConfigurationNode>();
        for (var index = 0; index < 5_000; index++) {
            children.add(new SanitizedConfigurationNode(
                    "parameter-" + index,
                    "large.group.parameter-" + index,
                    ConfigDriftViewerValueType.STRING,
                    ConfigDriftViewerValueType.STRING,
                    ConfigDriftViewerChangeKind.UNCHANGED,
                    ConfigDriftViewerSensitivity.NON_SENSITIVE,
                    "value-" + index,
                    "value-" + index,
                    null,
                    null,
                    List.of()
            ));
        }
        var document = new SanitizedConfigurationDocument(
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                "backend/application.yml.kv",
                "backend/application.yml.kv",
                1,
                true,
                true,
                "profile-large",
                "profile-large",
                new SanitizedConfigurationNode(
                        "root",
                        "",
                        ConfigDriftViewerValueType.MAP,
                        ConfigDriftViewerValueType.MAP,
                        ConfigDriftViewerChangeKind.UNCHANGED,
                        ConfigDriftViewerSensitivity.NON_SENSITIVE,
                        null,
                        null,
                        5_000,
                        5_000,
                        children
                )
        );
        var context = new pl.mkn.tdw.features.configdriftviewer.deterministic.model
                .ConfigDriftViewerDeterministicContext(
                baseline.repositoryId(),
                baseline.systemId(),
                baseline.systemLabel(),
                baseline.configurationDirectory(),
                baseline.sourceBranch(),
                baseline.targetBranch(),
                baseline.status(),
                baseline.sourceCoverage(),
                baseline.targetCoverage(),
                List.of(document),
                baseline.references(),
                baseline.differences(),
                baseline.findings()
        );

        var result = service.render(context, null);
        var tree = result.contents().get("config-drift-viewer/configuration-tree.yaml");
        @SuppressWarnings("unchecked")
        var parsed = (Map<String, Object>) new Yaml().load(tree);

        assertThat(tree.length())
                .isLessThanOrEqualTo(ConfigDriftViewerAiArtifactService.MAX_STRUCTURE_CHARACTERS);
        assertThat(parsed.get("truncated")).isEqualTo(true);
        assertThat(result.visibilityLimits())
                .anyMatch(limit -> limit.contains("configuration tree was truncated"));
        assertThat(context.documents().get(0).root().children()).hasSize(5_000);
        assertThat(context.differences()).isEqualTo(baseline.differences());
        assertThat(context.findings()).isEqualTo(baseline.findings());
    }
}
