package pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiTestFixtures;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationAiArtifactServiceTest {

    private final RuntimeConfigurationAiArtifactService service =
            new RuntimeConfigurationAiArtifactService(new ObjectMapper());

    @Test
    void shouldRenderFullSanitizedSchemaIncludingUnchangedParametersWithoutSecretsOrHashes() {
        var result = service.render(
                RuntimeConfigurationVerificationMode.BASIC,
                RuntimeConfigurationAiTestFixtures.deterministic(
                        pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                                .RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
                ),
                null
        );

        var all = String.join("\n", result.contents().values());
        assertThat(all)
                .contains("\"name\" : \"timeout\"")
                .contains("\"relation\" : \"UNCHANGED\"")
                .contains("\"sourceProfileToken\" : \"profile-1\"")
                .contains("\"documentIndex\" : 0")
                .contains("\"sourceValueToken\" : \"value-1\"")
                .contains("\"sourceValue\" : \"MASKED\"")
                .doesNotContain("raw-secret-source")
                .doesNotContain("raw-secret-target")
                .doesNotContain("raw-difference-secret-source")
                .doesNotContain("raw-difference-secret-target")
                .doesNotContainIgnoringCase("hmac")
                .doesNotContainIgnoringCase("sha256")
                .doesNotContainIgnoringCase("\"hash\"");
        assertThat(result.contents()).doesNotContainKey("runtime-configuration/deep-context.json");
    }

    @Test
    void shouldGroupAndMarkTruncatedLargeManifestWithoutChangingDeterministicResult() {
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
                    "token-" + index,
                    "token-" + index,
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

        assertThat(result.contents().get(
                "runtime-configuration/manifest/application_yaml-document-1.json"
        )).contains("\"truncated\":true");
        assertThat(result.visibilityLimits()).anyMatch(limit -> limit.contains("manifest group"));
        assertThat(context.differences()).isEqualTo(baseline.differences());
        assertThat(context.findings()).isEqualTo(baseline.findings());
    }
}
