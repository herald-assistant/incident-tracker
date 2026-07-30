package pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiTestFixtures;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDifference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source
        .RuntimeConfigurationFileRole;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api
        .RuntimeConfigurationVerificationMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationAiArtifactServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuntimeConfigurationAiArtifactService service =
            new RuntimeConfigurationAiArtifactService(objectMapper);

    @Test
    void shouldRenderCompactV2TreeWithAllParametersAndWithoutSecretsOrLegacyManifests()
            throws Exception {
        var result = service.render(
                RuntimeConfigurationVerificationMode.BASIC,
                RuntimeConfigurationAiTestFixtures.deterministic(
                        pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                                .RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
                ),
                null
        );

        assertThat(result.contents().keySet()).containsExactly(
                "runtime-configuration/scope.json",
                "runtime-configuration/coverage.json",
                "runtime-configuration/configuration-tree.yaml",
                "runtime-configuration/changes.json",
                "runtime-configuration/response-contract.json"
        );
        assertThat(result.contents().keySet())
                .noneMatch(name -> name.contains("/manifest/") || name.contains("manifest-index"));

        var tree = result.contents().get("runtime-configuration/configuration-tree.yaml");
        var changes = result.contents().get("runtime-configuration/changes.json");
        var all = String.join("\n", result.contents().values());

        assertThat(tree)
                .contains(
                        "formatVersion: 2",
                        "relationCodes:",
                        "typeCodes:",
                        "\"timeout\"",
                        "\"password\"",
                        "\"p:value-1\"",
                        "M: sensitive scalar suppressed before AI"
                );
        assertThat((Object) new Yaml().load(tree)).isInstanceOf(Map.class);
        assertThat(objectMapper.readTree(changes).get("formatVersion").asInt()).isEqualTo(2);
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
        var baseline = RuntimeConfigurationAiTestFixtures.deterministic(
                pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                        .RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
        );
        var added = new RuntimeConfigurationDifference(
                "difference-added",
                RuntimeConfigurationFileRole.APPLICATION_YAML,
                0,
                "feature.new-setting",
                RuntimeConfigurationChangeKind.ADDED,
                null,
                RuntimeConfigurationValueType.STRING,
                RuntimeConfigurationSensitivity.NON_SENSITIVE,
                null,
                "target-token"
        );
        var removed = new RuntimeConfigurationDifference(
                "difference-removed",
                RuntimeConfigurationFileRole.APPLICATION_YAML,
                0,
                "feature.old-setting",
                RuntimeConfigurationChangeKind.REMOVED,
                RuntimeConfigurationValueType.STRING,
                null,
                RuntimeConfigurationSensitivity.NON_SENSITIVE,
                "source-token",
                null
        );
        var context = new pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                .RuntimeConfigurationDeterministicContext(
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

        var result = service.render(RuntimeConfigurationVerificationMode.BASIC, context, null);
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
        var baseline = RuntimeConfigurationAiTestFixtures.deterministic(
                pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                        .RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
        );
        var children = new ArrayList<SanitizedConfigurationNode>();
        for (var index = 0; index < 5_000; index++) {
            children.add(new SanitizedConfigurationNode(
                    "parameter-" + index,
                    "large.group.parameter-" + index,
                    RuntimeConfigurationValueType.STRING,
                    RuntimeConfigurationValueType.STRING,
                    RuntimeConfigurationChangeKind.UNCHANGED,
                    RuntimeConfigurationSensitivity.NON_SENSITIVE,
                    "value-" + index,
                    "value-" + index,
                    null,
                    null,
                    List.of()
            ));
        }
        var document = new SanitizedConfigurationDocument(
                RuntimeConfigurationFileRole.APPLICATION_YAML,
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
                        RuntimeConfigurationValueType.MAP,
                        RuntimeConfigurationValueType.MAP,
                        RuntimeConfigurationChangeKind.UNCHANGED,
                        RuntimeConfigurationSensitivity.NON_SENSITIVE,
                        null,
                        null,
                        5_000,
                        5_000,
                        children
                )
        );
        var context = new pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                .RuntimeConfigurationDeterministicContext(
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

        var result = service.render(RuntimeConfigurationVerificationMode.BASIC, context, null);
        var tree = result.contents().get("runtime-configuration/configuration-tree.yaml");
        @SuppressWarnings("unchecked")
        var parsed = (Map<String, Object>) new Yaml().load(tree);

        assertThat(tree.length())
                .isLessThanOrEqualTo(RuntimeConfigurationAiArtifactService.MAX_STRUCTURE_CHARACTERS);
        assertThat(parsed.get("truncated")).isEqualTo(true);
        assertThat(result.visibilityLimits())
                .anyMatch(limit -> limit.contains("configuration tree was truncated"));
        assertThat(context.documents().get(0).root().children()).hasSize(5_000);
        assertThat(context.differences()).isEqualTo(baseline.differences());
        assertThat(context.findings()).isEqualTo(baseline.findings());
    }
}
