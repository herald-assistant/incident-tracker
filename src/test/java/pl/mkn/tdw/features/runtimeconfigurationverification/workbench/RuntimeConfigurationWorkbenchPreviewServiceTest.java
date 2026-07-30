package pl.mkn.tdw.features.runtimeconfigurationverification.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiTestFixtures;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation
        .RuntimeConfigurationAiArtifactService;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation
        .RuntimeConfigurationPromptPreparationService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.RuntimeConfigurationDeepContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source
        .RuntimeConfigurationDeterministicContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api
        .RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchAnonymizationPage.ValueRepresentation;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeConfigurationWorkbenchPreviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RuntimeConfigurationScopeResolver scopeResolver =
            mock(RuntimeConfigurationScopeResolver.class);
    private final RuntimeConfigurationDeterministicContextService deterministicService =
            mock(RuntimeConfigurationDeterministicContextService.class);
    private final RuntimeConfigurationDeepContextService deepService =
            mock(RuntimeConfigurationDeepContextService.class);

    private RuntimeConfigurationWorkbenchPreviewService service;

    @BeforeEach
    void setUp() {
        var store = new RuntimeConfigurationWorkbenchPreviewStore(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(10),
                32
        );
        service = new RuntimeConfigurationWorkbenchPreviewService(
                scopeResolver,
                deterministicService,
                deepService,
                new RuntimeConfigurationPromptPreparationService(
                        new RuntimeConfigurationAiArtifactService(objectMapper)
                ),
                store
        );
        when(scopeResolver.resolve("runtime-config", "billing-api")).thenReturn(scope());
        when(deterministicService.build(scope(), "dev1", "zt001"))
                .thenReturn(RuntimeConfigurationAiTestFixtures.deterministic(
                        RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
                ));
    }

    @Test
    void shouldReturnOnlyCompactBasicSummaryAndServeSanitizedDetailsLazily() throws Exception {
        var result = service.preview(request(RuntimeConfigurationVerificationMode.BASIC));
        var serialized = objectMapper.writeValueAsString(result);

        assertThat(result.previewId()).matches("[a-f0-9-]{36}");
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(result.source().sourceComplete()).isTrue();
        assertThat(result.source().targetComplete()).isTrue();
        assertThat(result.counts().documents()).isEqualTo(1);
        assertThat(result.counts().nodes()).isEqualTo(3);
        assertThat(result.counts().differences()).isEqualTo(1);
        assertThat(result.counts().findings()).isEqualTo(1);
        assertThat(result.artifacts())
                .extracting(artifact -> artifact.name())
                .containsExactly(
                        "runtime-configuration/scope.json",
                        "runtime-configuration/coverage.json",
                        "runtime-configuration/configuration-tree.yaml",
                        "runtime-configuration/changes.json",
                        "runtime-configuration/response-contract.json"
                );
        assertThat(serialized.length()).isLessThan(50_000);
        var responseJson = objectMapper.readTree(serialized);
        assertThat(responseJson.has("preparedPrompt")).isFalse();
        assertThat(responseJson.has("artifactContents")).isFalse();
        assertThat(responseJson.has("mapping")).isFalse();
        assertThat(responseJson.has("anonymizationDecisions")).isFalse();
        assertThat(serialized)
                .doesNotContain(
                        "\"decisions\"",
                        "raw-secret-source",
                        "raw-secret-target",
                        "raw-difference-secret-source",
                        "raw-difference-secret-target"
                );

        var source = service.source(result.previewId());
        assertThat(source.source().files()).hasSize(3);
        assertThat(source.target().files()).hasSize(3);

        var changedMapping = service.mapping(result.previewId(), 0, 100, true);
        assertThat(changedMapping.totalNodes()).isEqualTo(3);
        assertThat(changedMapping.totalItems()).isEqualTo(2);
        assertThat(changedMapping.items())
                .extracting(item -> item.path())
                .containsExactly("", "datasource.password");

        var allMapping = service.mapping(result.previewId(), 0, 2, false);
        assertThat(allMapping.totalItems()).isEqualTo(3);
        assertThat(allMapping.items()).hasSize(2);

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
        assertThat(anonymization.items())
                .filteredOn(item -> "service.timeout".equals(item.path()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.sourceRepresentation())
                            .isEqualTo(ValueRepresentation.PSEUDONYMIZED);
                    assertThat(item.sourceValueToken()).isEqualTo("value-1");
                });

        var aiInput = service.aiInput(result.previewId());
        assertThat(aiInput.characterCount()).isEqualTo(aiInput.prompt().length());
        assertThat(aiInput.prompt())
                .contains("runtime-configuration-basic-review")
                .doesNotContain("raw-secret-source", "raw-secret-target");

        var tree = service.artifact(
                result.previewId(),
                "runtime-configuration/configuration-tree.yaml"
        );
        assertThat(tree.mediaType()).isEqualTo("application/yaml");
        assertThat(tree.content())
                .contains(
                        "\"timeout\"",
                        "\"p:value-1\"",
                        "M: sensitive scalar suppressed before AI"
                )
                .doesNotContain("raw-secret-source", "raw-secret-target");
        verify(deterministicService).build(scope(), "dev1", "zt001");
        verify(deepService, never()).build(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void shouldIncludeScopedDeepSummaryAndExposeDeepDetailOnDemand() {
        var deep = RuntimeConfigurationAiTestFixtures.deep();
        when(deepService.build(
                eq(RuntimeConfigurationVerificationMode.DEEP),
                eq("runtime-config"),
                eq("billing-api"),
                eq("release-42"),
                any()
        )).thenReturn(Optional.of(deep));

        var result = service.preview(request(RuntimeConfigurationVerificationMode.DEEP));

        assertThat(result.deep().requested()).isTrue();
        assertThat(result.deep().status()).isEqualTo("COMPLETE");
        assertThat(result.deep().preflightStatus()).isEqualTo("READY");
        assertThat(result.deep().repositoryScopes()).isEqualTo(1);
        assertThat(result.deep().codeGroundings()).isEqualTo(1);
        assertThat(result.artifacts())
                .extracting(artifact -> artifact.name())
                .contains("runtime-configuration/deep-context.json");
        assertThat(service.deep(result.previewId()).context()).isEqualTo(deep);
        assertThat(service.aiInput(result.previewId()).prompt())
                .contains("runtime-configuration-deep-review")
                .contains("runtime-configuration/deep-context.json");
        assertThat(result.visibilityLimits())
                .contains("The code ref is not confirmed as deployed.");
    }

    @Test
    void shouldKeepBasicSnapshotAvailableWhenDeepEnrichmentFails() throws Exception {
        when(deepService.build(any(), anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("token=unsafe-deep-secret"));

        var result = service.preview(request(RuntimeConfigurationVerificationMode.DEEP));
        var serialized = objectMapper.writeValueAsString(result);

        assertThat(result.deep().requested()).isTrue();
        assertThat(result.deep().status()).isEqualTo("UNAVAILABLE");
        assertThat(service.deep(result.previewId()).context()).isNull();
        assertThat(service.mapping(result.previewId(), 0, 100, true).items()).isNotEmpty();
        assertThat(result.visibilityLimits()).contains(
                "DEEP enrichment did not complete; deterministic mapping and BASIC-safe AI input remain available."
        );
        assertThat(serialized).doesNotContain("unsafe-deep-secret");
    }

    @Test
    void shouldReplaceUnsafeDeterministicFailureWithStableUserFacingError() {
        when(deterministicService.build(scope(), "dev1", "zt001"))
                .thenThrow(new IllegalStateException("password=raw-source-secret"));

        assertThatThrownBy(() -> service.preview(request(RuntimeConfigurationVerificationMode.BASIC)))
                .isInstanceOf(RuntimeConfigurationWorkbenchPreviewException.class)
                .hasMessage("Runtime configuration preview did not complete. Check source coverage and retry.")
                .hasMessageNotContaining("raw-source-secret");
    }

    @Test
    void shouldRejectUnknownArtifactWithoutDisclosingSnapshotContents() {
        var result = service.preview(request(RuntimeConfigurationVerificationMode.BASIC));

        assertThatThrownBy(() -> service.artifact(result.previewId(), "unknown.json"))
                .isInstanceOf(RuntimeConfigurationWorkbenchPreviewNotFoundException.class)
                .hasMessage("Runtime configuration preview is missing or expired. Run a new preview.");
    }

    private RuntimeConfigurationWorkbenchPreviewRequest request(
            RuntimeConfigurationVerificationMode mode
    ) {
        return new RuntimeConfigurationWorkbenchPreviewRequest(
                mode,
                "runtime-config",
                "billing-api",
                "dev1",
                "zt001",
                mode == RuntimeConfigurationVerificationMode.DEEP ? "release-42" : null
        );
    }

    private RuntimeConfigurationScope scope() {
        return new RuntimeConfigurationScope(
                "runtime-config",
                "config-gitlab",
                "platform/runtime-config",
                "billing-api",
                "Billing API",
                "backend"
        );
    }
}
