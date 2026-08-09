package pl.mkn.tdw.features.configdriftviewer.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.ai.ConfigDriftViewerAiTestFixtures;
import pl.mkn.tdw.features.configdriftviewer.ai.preparation
        .ConfigDriftViewerAiArtifactService;
import pl.mkn.tdw.features.configdriftviewer.ai.preparation
        .ConfigDriftViewerPromptPreparationService;
import pl.mkn.tdw.features.configdriftviewer.deep.ConfigDriftViewerDeepContextService;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffFile;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffFileFormat;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjection;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffValue;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffValuePresence;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source
        .ConfigDriftViewerDeterministicBuildResult;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source
        .ConfigDriftViewerDeterministicContextService;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source
        .ConfigDriftViewerFileRole;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.job.api
        .ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScope;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScopeResolver;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchAnonymizationPage.ValueRepresentation;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchPreviewRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigDriftViewerWorkbenchPreviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ConfigDriftViewerScopeResolver scopeResolver =
            mock(ConfigDriftViewerScopeResolver.class);
    private final ConfigDriftViewerDeterministicContextService deterministicService =
            mock(ConfigDriftViewerDeterministicContextService.class);
    private final ConfigDriftViewerDeepContextService deepService =
            mock(ConfigDriftViewerDeepContextService.class);

    private ConfigDriftViewerPromptPreparationService promptService;
    private ConfigDriftViewerWorkbenchPreviewService service;

    @BeforeEach
    void setUp() {
        var store = new ConfigDriftViewerWorkbenchPreviewStore(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(10),
                32
        );
        promptService = spy(new ConfigDriftViewerPromptPreparationService(
                new ConfigDriftViewerAiArtifactService(objectMapper)
        ));
        service = new ConfigDriftViewerWorkbenchPreviewService(
                scopeResolver,
                deterministicService,
                deepService,
                promptService,
                store
        );
        when(scopeResolver.resolve("runtime-config", "crm-api")).thenReturn(scope());
        when(deterministicService.build(scope(), "dev1", "zt001"))
                .thenReturn(new ConfigDriftViewerDeterministicBuildResult(
                        ConfigDriftViewerAiTestFixtures.deterministic(
                                ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
                        ),
                        configurationDiff()
                ));
    }

    @Test
    void shouldStopBasicAfterOperatorProjectionWithoutGeneratingAiInput() throws Exception {
        var result = service.preview(request(ConfigDriftViewerMode.BASIC));
        var serialized = objectMapper.writeValueAsString(result);

        assertThat(result.previewId()).matches("[a-f0-9-]{36}");
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(result.aiInputGenerated()).isFalse();
        assertThat(result.source().sourceComplete()).isTrue();
        assertThat(result.source().targetComplete()).isTrue();
        assertThat(result.counts().documents()).isEqualTo(1);
        assertThat(result.counts().nodes()).isEqualTo(3);
        assertThat(result.counts().differences()).isEqualTo(1);
        assertThat(result.counts().findings()).isEqualTo(1);
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.anonymization().totalNodes()).isZero();
        assertThat(serialized.length()).isLessThan(50_000);
        var responseJson = objectMapper.readTree(serialized);
        assertThat(responseJson.has("preparedPrompt")).isFalse();
        assertThat(responseJson.has("artifactContents")).isFalse();
        assertThat(responseJson.has("configurationDiff")).isFalse();
        assertThat(responseJson.has("mapping")).isFalse();
        assertThat(responseJson.has("anonymizationDecisions")).isFalse();
        assertThat(serialized)
                .doesNotContain(
                        "\"decisions\"",
                        "${VAULT_DYNAMIC_DEV}",
                        "${VAULT_DYNAMIC_ZT}",
                        "raw-secret-source",
                        "raw-secret-target",
                        "raw-difference-secret-source",
                        "raw-difference-secret-target"
                );

        var source = service.source(result.previewId());
        assertThat(source.source().files()).hasSize(3);
        assertThat(source.target().files()).hasSize(3);

        var operatorProjection = service.configurationDiff(result.previewId());
        assertThat(operatorProjection.configurationDiff()).isEqualTo(configurationDiff());
        assertThat(objectMapper.writeValueAsString(operatorProjection))
                .contains(
                        "clients.customer-zt001.password",
                        "${VAULT_DYNAMIC_DEV}",
                        "${VAULT_DYNAMIC_ZT}"
                );

        assertThat(service.mapping(result.previewId(), 0, 100, false).items()).isEmpty();
        assertThat(service.anonymization(result.previewId(), 0, 100).items()).isEmpty();

        var aiInput = service.aiInput(result.previewId());
        assertThat(aiInput.generated()).isFalse();
        assertThat(aiInput.characterCount()).isZero();
        assertThat(aiInput.prompt()).isNull();

        verify(deterministicService).build(scope(), "dev1", "zt001");
        verify(deepService, never()).build(any(), anyString(), anyString(), any(), any());
        verify(promptService, never()).prepare(any(), any(), any());
    }

    @Test
    void shouldExposeProjectionAndSanitizedAiBoundaryForDeep() throws Exception {
        var deep = ConfigDriftViewerAiTestFixtures.deep();
        when(deepService.build(
                eq(ConfigDriftViewerMode.DEEP),
                eq("runtime-config"),
                eq("crm-api"),
                eq("release-42"),
                any()
        )).thenReturn(Optional.of(deep));

        var result = service.preview(request(ConfigDriftViewerMode.DEEP));

        assertThat(result.aiInputGenerated()).isTrue();
        assertThat(result.deep().requested()).isTrue();
        assertThat(result.deep().status()).isEqualTo("COMPLETE");
        assertThat(result.deep().preflightStatus()).isEqualTo("READY");
        assertThat(result.deep().repositoryScopes()).isEqualTo(1);
        assertThat(result.deep().codeGroundings()).isEqualTo(1);
        assertThat(result.artifacts())
                .extracting(artifact -> artifact.name())
                .contains("runtime-configuration/deep-context.json");
        assertThat(service.configurationDiff(result.previewId()).configurationDiff())
                .isEqualTo(configurationDiff());
        assertThat(service.deep(result.previewId()).context()).isEqualTo(deep);
        var changedMapping = service.mapping(result.previewId(), 0, 100, true);
        assertThat(changedMapping.totalNodes()).isEqualTo(3);
        assertThat(changedMapping.totalItems()).isEqualTo(2);
        assertThat(changedMapping.items())
                .filteredOn(item -> "clients.customer-zt001.password".equals(item.originalPath()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.sanitizedPath()).isEqualTo("datasource.password");
                    assertThat(item.differenceIds()).containsExactly("difference-1");
                    assertThat(item.sourceValueToken()).isNull();
                    assertThat(item.targetValueToken()).isNull();
                });
        var allMapping = service.mapping(result.previewId(), 0, 100, false);
        assertThat(allMapping.totalItems()).isEqualTo(3);
        assertThat(allMapping.items())
                .filteredOn(item -> "service.timeout".equals(item.originalPath()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.sanitizedPath()).isEqualTo("service.timeout");
                    assertThat(item.sourceValueToken()).isEqualTo("value-1");
                    assertThat(item.targetValueToken()).isEqualTo("value-1");
                });

        var anonymization = service.anonymization(result.previewId(), 0, 100);
        assertThat(anonymization.totalItems()).isEqualTo(3);
        assertThat(anonymization.items())
                .filteredOn(item -> "datasource.password".equals(item.path()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.sourceRepresentation()).isEqualTo(ValueRepresentation.SUPPRESSED);
                    assertThat(item.targetRepresentation()).isEqualTo(ValueRepresentation.SUPPRESSED);
                    assertThat(item.sourceValueToken()).isNull();
                    assertThat(item.targetValueToken()).isNull();
                });

        var aiInput = service.aiInput(result.previewId());
        assertThat(aiInput.generated()).isTrue();
        assertThat(aiInput.prompt())
                .contains("config-drift-viewer-deep-review")
                .contains("runtime-configuration/deep-context.json")
                .doesNotContain("${VAULT_DYNAMIC_DEV}", "${VAULT_DYNAMIC_ZT}");
        var tree = service.artifact(
                result.previewId(),
                "config-drift-viewer/configuration-tree.yaml"
        );
        assertThat(tree.content())
                .contains("\"p:value-1\"", "M: sensitive scalar suppressed before AI")
                .doesNotContain("${VAULT_DYNAMIC_DEV}", "${VAULT_DYNAMIC_ZT}");
        assertThat(result.visibilityLimits())
                .contains("The code ref is not confirmed as deployed.");
        verify(promptService).prepare(any(), any(), any());
    }

    @Test
    void shouldKeepDeterministicSnapshotAvailableWhenDeepEnrichmentFails() throws Exception {
        when(deepService.build(any(), anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("token=unsafe-deep-secret"));

        var result = service.preview(request(ConfigDriftViewerMode.DEEP));
        var serialized = objectMapper.writeValueAsString(result);

        assertThat(result.deep().requested()).isTrue();
        assertThat(result.deep().status()).isEqualTo("UNAVAILABLE");
        assertThat(result.aiInputGenerated()).isTrue();
        assertThat(service.deep(result.previewId()).context()).isNull();
        assertThat(service.mapping(result.previewId(), 0, 100, true).items()).isNotEmpty();
        assertThat(result.visibilityLimits()).contains(
                "DEEP enrichment did not complete; deterministic projection and sanitized AI input remain available."
        );
        assertThat(serialized).doesNotContain("unsafe-deep-secret");
    }

    @Test
    void shouldReplaceUnsafeDeterministicFailureWithStableUserFacingError() {
        when(deterministicService.build(scope(), "dev1", "zt001"))
                .thenThrow(new IllegalStateException("password=raw-source-secret"));

        assertThatThrownBy(() -> service.preview(request(ConfigDriftViewerMode.BASIC)))
                .isInstanceOf(ConfigDriftViewerWorkbenchPreviewException.class)
                .hasMessage("Runtime configuration preview did not complete. Check source coverage and retry.")
                .hasMessageNotContaining("raw-source-secret");
    }

    @Test
    void shouldRejectUnknownArtifactWithoutDisclosingSnapshotContents() {
        var result = service.preview(request(ConfigDriftViewerMode.BASIC));

        assertThatThrownBy(() -> service.artifact(result.previewId(), "unknown.json"))
                .isInstanceOf(ConfigDriftViewerWorkbenchPreviewNotFoundException.class)
                .hasMessage("Runtime configuration preview is missing or expired. Run a new preview.");
    }

    private ConfigDriftViewerWorkbenchPreviewRequest request(
            ConfigDriftViewerMode mode
    ) {
        return new ConfigDriftViewerWorkbenchPreviewRequest(
                mode,
                "runtime-config",
                "crm-api",
                "dev1",
                "zt001",
                mode == ConfigDriftViewerMode.DEEP ? "release-42" : null
        );
    }

    private ConfigDriftViewerScope scope() {
        return new ConfigDriftViewerScope(
                "runtime-config",
                "config-gitlab",
                "platform/runtime-config",
                "crm-api",
                "CRM API",
                "backend"
        );
    }

    private ConfigDriftViewerDiffProjection configurationDiff() {
        var timeout = new ConfigDriftViewerDiffNode(
                "timeout",
                "service.timeout",
                ConfigDriftViewerChangeKind.UNCHANGED,
                value(ConfigDriftViewerValueType.NUMBER, 30),
                value(ConfigDriftViewerValueType.NUMBER, 30),
                List.of(),
                List.of()
        );
        var dynamicPassword = new ConfigDriftViewerDiffNode(
                "password",
                "clients.customer-zt001.password",
                ConfigDriftViewerChangeKind.CHANGED,
                value(ConfigDriftViewerValueType.STRING, "${VAULT_DYNAMIC_DEV}"),
                value(ConfigDriftViewerValueType.STRING, "${VAULT_DYNAMIC_ZT}"),
                List.of("difference-1"),
                List.of()
        );
        var root = new ConfigDriftViewerDiffNode(
                "root",
                "",
                ConfigDriftViewerChangeKind.CHANGED,
                collectionValue(ConfigDriftViewerValueType.MAP, 2),
                collectionValue(ConfigDriftViewerValueType.MAP, 2),
                List.of(),
                List.of(timeout, dynamicPassword)
        );
        return new ConfigDriftViewerDiffProjection(
                "dev1",
                "zt001",
                List.of(new ConfigDriftViewerDiffFile(
                        ConfigDriftViewerFileRole.APPLICATION_YAML,
                        ConfigDriftViewerDiffFileFormat.YAML,
                        "backend/application.yml.kv",
                        "backend/application.yml.kv",
                        true,
                        true,
                        List.of(new ConfigDriftViewerDiffDocument(
                                0,
                                true,
                                true,
                                ConfigDriftViewerDiffValue.absent(),
                                ConfigDriftViewerDiffValue.absent(),
                                root
                        ))
                ))
        );
    }

    private ConfigDriftViewerDiffValue value(
            ConfigDriftViewerValueType type,
            Object value
    ) {
        return new ConfigDriftViewerDiffValue(
                ConfigDriftViewerDiffValuePresence.PRESENT,
                type,
                value,
                null
        );
    }

    private ConfigDriftViewerDiffValue collectionValue(
            ConfigDriftViewerValueType type,
            int cardinality
    ) {
        return new ConfigDriftViewerDiffValue(
                ConfigDriftViewerDiffValuePresence.PRESENT,
                type,
                null,
                cardinality
        );
    }
}
