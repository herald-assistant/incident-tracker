package pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiTestFixtures;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDifference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationFinding;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationFindingSeverity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationReference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationReferenceStatus;
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
        .RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api
        .RuntimeConfigurationVerificationMode;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationPromptPreparationServiceTest {

    private final RuntimeConfigurationPromptPreparationService service =
            new RuntimeConfigurationPromptPreparationService(
                    new RuntimeConfigurationAiArtifactService(new ObjectMapper())
            );

    @Test
    void shouldPrepareBasicPromptWithCompactV2ArtifactsOnly() {
        var preparation = service.prepare(
                request(RuntimeConfigurationVerificationMode.BASIC),
                RuntimeConfigurationAiTestFixtures.deterministic(
                        RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
                ),
                RuntimeConfigurationAiTestFixtures.deep()
        );

        assertThat(preparation.prompt())
                .contains("runtime-configuration-basic-review")
                .contains("Nie używaj Operational Context ani kodu")
                .contains("parametry zmienione i niezmienione")
                .contains("runtime-configuration/configuration-tree.yaml")
                .contains("runtime-configuration/changes.json")
                .doesNotContain("runtime-configuration/deep-context.json")
                .doesNotContain("runtime-configuration/manifest/");
        assertThat(preparation.artifactContents())
                .doesNotContainKey("runtime-configuration/deep-context.json");
    }

    @Test
    void shouldPrepareDeepPromptWithContextCodeAndOwnershipLimitations() {
        var preparation = service.prepare(
                request(RuntimeConfigurationVerificationMode.DEEP),
                RuntimeConfigurationAiTestFixtures.deterministic(
                        RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
                ),
                RuntimeConfigurationAiTestFixtures.deep()
        );

        assertThat(preparation.prompt())
                .contains("runtime-configuration-deep-review")
                .contains("focused verification")
                .contains("Ownership jest faktem backendowym")
                .contains("runtime-configuration/deep-context.json");
        assertThat(preparation.artifactContents())
                .containsKey("runtime-configuration/deep-context.json");
    }

    @Test
    void shouldStayBelowTargetBudgetAndPreserveRepresentativeFixtureCoverage() {
        var preparation = service.prepare(
                request(RuntimeConfigurationVerificationMode.BASIC),
                representativeContext(),
                null
        );

        assertThat(preparation.prompt().length()).isLessThan(150_000);
        assertThat(preparation.artifactContents()
                .get("runtime-configuration/configuration-tree.yaml"))
                .contains("\"setting-853\"")
                .doesNotContain("truncated: true");
        assertThat(preparation.artifactContents().get("runtime-configuration/changes.json"))
                .contains("\"difference-135\"", "\"finding-102\"", "\"reference-89\"")
                .doesNotContain("\"truncated\":true");
    }

    private RuntimeConfigurationDeterministicContext representativeContext() {
        var baseline = RuntimeConfigurationAiTestFixtures.deterministic(
                RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
        );
        var children = IntStream.range(0, 854)
                .mapToObj(index -> new SanitizedConfigurationNode(
                        "setting-%03d".formatted(index),
                        "services.module-%03d.setting-%03d".formatted(index, index),
                        RuntimeConfigurationValueType.STRING,
                        RuntimeConfigurationValueType.STRING,
                        index < 136
                                ? RuntimeConfigurationChangeKind.CHANGED
                                : RuntimeConfigurationChangeKind.UNCHANGED,
                        RuntimeConfigurationSensitivity.NON_SENSITIVE,
                        "v-%03d".formatted(index),
                        index < 136 ? "t-%03d".formatted(index) : "v-%03d".formatted(index),
                        null,
                        null,
                        List.of()
                ))
                .toList();
        var document = new SanitizedConfigurationDocument(
                RuntimeConfigurationFileRole.APPLICATION_YAML,
                "backend/application.yml.kv",
                "backend/application.yml.kv",
                0,
                true,
                true,
                null,
                null,
                new SanitizedConfigurationNode(
                        "root",
                        "",
                        RuntimeConfigurationValueType.MAP,
                        RuntimeConfigurationValueType.MAP,
                        RuntimeConfigurationChangeKind.CHANGED,
                        RuntimeConfigurationSensitivity.NON_SENSITIVE,
                        null,
                        null,
                        children.size(),
                        children.size(),
                        children
                )
        );
        var differences = IntStream.range(0, 136)
                .mapToObj(index -> new RuntimeConfigurationDifference(
                        "difference-" + index,
                        RuntimeConfigurationFileRole.APPLICATION_YAML,
                        0,
                        "services.module-%03d.setting-%03d".formatted(index, index),
                        RuntimeConfigurationChangeKind.CHANGED,
                        RuntimeConfigurationValueType.STRING,
                        RuntimeConfigurationValueType.STRING,
                        RuntimeConfigurationSensitivity.NON_SENSITIVE,
                        "v-%03d".formatted(index),
                        "t-%03d".formatted(index)
                ))
                .toList();
        var findings = IntStream.range(0, 103)
                .mapToObj(index -> new RuntimeConfigurationFinding(
                        "finding-" + index,
                        "CONFIGURATION_VALUE_CHANGED",
                        RuntimeConfigurationFindingSeverity.WARNING,
                        "services.module-%03d.setting-%03d".formatted(index, index),
                        List.of("difference-" + index),
                        List.of()
                ))
                .toList();
        var references = IntStream.range(0, 90)
                .mapToObj(index -> new RuntimeConfigurationReference(
                        "reference-" + index,
                        RuntimeConfigurationFileRole.APPLICATION_YAML,
                        0,
                        "services.module-%03d.setting-%03d".formatted(index, index),
                        "GLOBAL_SETTING_%03d".formatted(index),
                        "VARIABLE",
                        RuntimeConfigurationReferenceStatus.RESOLVED,
                        RuntimeConfigurationReferenceStatus.RESOLVED
                ))
                .toList();
        return new RuntimeConfigurationDeterministicContext(
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
                references,
                differences,
                findings
        );
    }

    private RuntimeConfigurationVerificationJobStartRequest request(
            RuntimeConfigurationVerificationMode mode
    ) {
        return new RuntimeConfigurationVerificationJobStartRequest(
                mode,
                "runtime-config",
                "billing-api",
                "dev1",
                "zt001",
                "release-1",
                "gpt-5.4",
                "medium"
        );
    }
}
