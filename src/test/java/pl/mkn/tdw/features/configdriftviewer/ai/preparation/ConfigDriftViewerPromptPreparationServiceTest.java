package pl.mkn.tdw.features.configdriftviewer.ai.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.ai.ConfigDriftViewerAiTestFixtures;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerDifference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerFinding;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerFindingSeverity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerReference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerReferenceStatus;
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
import pl.mkn.tdw.features.configdriftviewer.job.api
        .ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api
        .ConfigDriftViewerMode;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigDriftViewerPromptPreparationServiceTest {

    private final ConfigDriftViewerPromptPreparationService service =
            new ConfigDriftViewerPromptPreparationService(
                    new ConfigDriftViewerAiArtifactService(new ObjectMapper())
            );

    @Test
    void shouldRejectBasicPromptPreparation() {
        assertThatThrownBy(() -> service.prepare(
                request(ConfigDriftViewerMode.BASIC),
                ConfigDriftViewerAiTestFixtures.deterministic(
                        ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
                ),
                ConfigDriftViewerAiTestFixtures.deep()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEEP");
    }

    @Test
    void shouldPrepareDeepPromptWithContextCodeAndOwnershipLimitations() {
        var preparation = service.prepare(
                request(ConfigDriftViewerMode.DEEP),
                ConfigDriftViewerAiTestFixtures.deterministic(
                        ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
                ),
                ConfigDriftViewerAiTestFixtures.deep()
        );

        assertThat(preparation.prompt())
                .contains("config-drift-viewer-deep-review")
                .contains("focused verification")
                .contains("Ownership jest faktem backendowym")
                .contains("runtime-configuration/deep-context.json");
        assertThat(preparation.artifactContents())
                .containsKey("runtime-configuration/deep-context.json");
    }

    @Test
    void shouldStayBelowTargetBudgetAndPreserveRepresentativeFixtureCoverage() {
        var preparation = service.prepare(
                request(ConfigDriftViewerMode.DEEP),
                representativeContext(),
                null
        );

        assertThat(preparation.prompt().length()).isLessThan(150_000);
        assertThat(preparation.artifactContents()
                .get("config-drift-viewer/configuration-tree.yaml"))
                .contains("\"setting-853\"")
                .doesNotContain("truncated: true");
        assertThat(preparation.artifactContents().get("runtime-configuration/changes.json"))
                .contains("\"difference-135\"", "\"finding-102\"", "\"reference-89\"")
                .doesNotContain("\"truncated\":true");
    }

    private ConfigDriftViewerDeterministicContext representativeContext() {
        var baseline = ConfigDriftViewerAiTestFixtures.deterministic(
                ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
        );
        var children = IntStream.range(0, 854)
                .mapToObj(index -> new SanitizedConfigurationNode(
                        "setting-%03d".formatted(index),
                        "services.module-%03d.setting-%03d".formatted(index, index),
                        ConfigDriftViewerValueType.STRING,
                        ConfigDriftViewerValueType.STRING,
                        index < 136
                                ? ConfigDriftViewerChangeKind.CHANGED
                                : ConfigDriftViewerChangeKind.UNCHANGED,
                        ConfigDriftViewerSensitivity.NON_SENSITIVE,
                        "v-%03d".formatted(index),
                        index < 136 ? "t-%03d".formatted(index) : "v-%03d".formatted(index),
                        null,
                        null,
                        List.of()
                ))
                .toList();
        var document = new SanitizedConfigurationDocument(
                ConfigDriftViewerFileRole.APPLICATION_YAML,
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
                        ConfigDriftViewerValueType.MAP,
                        ConfigDriftViewerValueType.MAP,
                        ConfigDriftViewerChangeKind.CHANGED,
                        ConfigDriftViewerSensitivity.NON_SENSITIVE,
                        null,
                        null,
                        children.size(),
                        children.size(),
                        children
                )
        );
        var differences = IntStream.range(0, 136)
                .mapToObj(index -> new ConfigDriftViewerDifference(
                        "difference-" + index,
                        ConfigDriftViewerFileRole.APPLICATION_YAML,
                        0,
                        "services.module-%03d.setting-%03d".formatted(index, index),
                        ConfigDriftViewerChangeKind.CHANGED,
                        ConfigDriftViewerValueType.STRING,
                        ConfigDriftViewerValueType.STRING,
                        ConfigDriftViewerSensitivity.NON_SENSITIVE,
                        "v-%03d".formatted(index),
                        "t-%03d".formatted(index)
                ))
                .toList();
        var findings = IntStream.range(0, 103)
                .mapToObj(index -> new ConfigDriftViewerFinding(
                        "finding-" + index,
                        "CONFIGURATION_VALUE_CHANGED",
                        ConfigDriftViewerFindingSeverity.WARNING,
                        "services.module-%03d.setting-%03d".formatted(index, index),
                        List.of("difference-" + index),
                        List.of()
                ))
                .toList();
        var references = IntStream.range(0, 90)
                .mapToObj(index -> new ConfigDriftViewerReference(
                        "reference-" + index,
                        ConfigDriftViewerFileRole.APPLICATION_YAML,
                        0,
                        "services.module-%03d.setting-%03d".formatted(index, index),
                        "GLOBAL_SETTING_%03d".formatted(index),
                        "VARIABLE",
                        ConfigDriftViewerReferenceStatus.RESOLVED,
                        ConfigDriftViewerReferenceStatus.RESOLVED
                ))
                .toList();
        return new ConfigDriftViewerDeterministicContext(
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

    private ConfigDriftViewerJobStartRequest request(
            ConfigDriftViewerMode mode
    ) {
        return new ConfigDriftViewerJobStartRequest(
                mode,
                "runtime-config",
                java.util.List.of("crm-api"),
                "dev1",
                "zt001",
                "release-1",
                "gpt-5.4",
                "medium"
        );
    }
}
