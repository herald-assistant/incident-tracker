package pl.mkn.tdw.features.runtimeconfigurationverification.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiTestFixtures;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationAiArtifactService;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparationService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.RuntimeConfigurationDeepContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewResponse.ValueRepresentation;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuntimeConfigurationScopeResolver scopeResolver =
            mock(RuntimeConfigurationScopeResolver.class);
    private final RuntimeConfigurationDeterministicContextService deterministicService =
            mock(RuntimeConfigurationDeterministicContextService.class);
    private final RuntimeConfigurationDeepContextService deepService =
            mock(RuntimeConfigurationDeepContextService.class);
    private final RuntimeConfigurationWorkbenchPreviewService service =
            new RuntimeConfigurationWorkbenchPreviewService(
                    scopeResolver,
                    deterministicService,
                    deepService,
                    new RuntimeConfigurationPromptPreparationService(
                            new RuntimeConfigurationAiArtifactService(objectMapper)
                    )
            );

    @BeforeEach
    void setUp() {
        when(scopeResolver.resolve("runtime-config", "billing-api")).thenReturn(scope());
        when(deterministicService.build(scope(), "dev1", "zt001"))
                .thenReturn(RuntimeConfigurationAiTestFixtures.deterministic(
                        pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                                .RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
                ));
    }

    @Test
    void shouldExposeBasicPipelineWithoutDeepCallsOrSensitiveTokens() throws Exception {
        var result = service.preview(request(RuntimeConfigurationVerificationMode.BASIC));
        var serialized = objectMapper.writeValueAsString(result);

        assertThat(result.sourceAcquisition().source().complete()).isTrue();
        assertThat(result.sourceAcquisition().target().complete()).isTrue();
        assertThat(result.mapping().documents()).hasSize(1);
        assertThat(result.artifactContents())
                .containsKeys(
                        "runtime-configuration/scope.json",
                        "runtime-configuration/coverage.json",
                        "runtime-configuration/differences-and-findings.json",
                        "runtime-configuration/manifest/application_yaml-document-0.json",
                        "runtime-configuration/response-contract.json"
                )
                .doesNotContainKey("runtime-configuration/deep-context.json");
        assertThat(result.preparedPrompt()).contains("runtime-configuration-basic-review", "value-1");
        assertThat(result.anonymization().totalNodes()).isEqualTo(3);
        assertThat(result.anonymization().pseudonymizedRepresentations()).isEqualTo(2);
        assertThat(result.anonymization().suppressedRepresentations()).isEqualTo(2);
        assertThat(result.anonymization().decisions())
                .filteredOn(decision -> "datasource.password".equals(decision.path()))
                .singleElement()
                .satisfies(decision -> {
                    assertThat(decision.sourceRepresentation()).isEqualTo(ValueRepresentation.SUPPRESSED);
                    assertThat(decision.targetRepresentation()).isEqualTo(ValueRepresentation.SUPPRESSED);
                    assertThat(decision.sourceValueToken()).isNull();
                    assertThat(decision.targetValueToken()).isNull();
                });
        assertThat(serialized)
                .doesNotContain(
                        "raw-secret-source",
                        "raw-secret-target",
                        "raw-difference-secret-source",
                        "raw-difference-secret-target"
                )
                .contains("value-1", "SUPPRESSED", "PSEUDONYMIZED");
        verify(deepService, never()).build(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void shouldIncludeScopedDeepContextInExactAiArtifacts() {
        var deep = RuntimeConfigurationAiTestFixtures.deep();
        when(deepService.build(
                eq(RuntimeConfigurationVerificationMode.DEEP),
                eq("runtime-config"),
                eq("billing-api"),
                eq("release-42"),
                any()
        )).thenReturn(Optional.of(deep));

        var result = service.preview(request(RuntimeConfigurationVerificationMode.DEEP));

        assertThat(result.deepContext()).isEqualTo(deep);
        assertThat(result.artifactContents())
                .containsKey("runtime-configuration/deep-context.json");
        assertThat(result.preparedPrompt())
                .contains("runtime-configuration-deep-review", "billing-api", "release-1");
        assertThat(result.visibilityLimits())
                .contains("The code ref is not confirmed as deployed.");
    }

    @Test
    void shouldReturnSafePartialPreviewWhenDeepEnrichmentFails() throws Exception {
        when(deepService.build(any(), anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("token=unsafe-deep-secret"));

        var result = service.preview(request(RuntimeConfigurationVerificationMode.DEEP));
        var serialized = objectMapper.writeValueAsString(result);

        assertThat(result.deepContext()).isNull();
        assertThat(result.mapping()).isNotNull();
        assertThat(result.visibilityLimits())
                .contains("DEEP enrichment did not complete; deterministic mapping and BASIC-safe AI input remain available.");
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
    void shouldExposeArtifactTruncationWithoutTruncatingDeterministicMapping() {
        var base = RuntimeConfigurationAiTestFixtures.deterministic(
                pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                        .RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED
        );
        var children = IntStream.range(0, 5_000)
                .mapToObj(index -> new SanitizedConfigurationNode(
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
                ))
                .toList();
        var document = new SanitizedConfigurationDocument(
                base.documents().get(0).role(),
                base.documents().get(0).sourcePath(),
                base.documents().get(0).targetPath(),
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
                        RuntimeConfigurationChangeKind.UNCHANGED,
                        RuntimeConfigurationSensitivity.NON_SENSITIVE,
                        null,
                        null,
                        children.size(),
                        children.size(),
                        children
                )
        );
        var large = new RuntimeConfigurationDeterministicContext(
                base.repositoryId(),
                base.systemId(),
                base.systemLabel(),
                base.configurationDirectory(),
                base.sourceBranch(),
                base.targetBranch(),
                base.status(),
                base.sourceCoverage(),
                base.targetCoverage(),
                List.of(document),
                base.references(),
                base.differences(),
                base.findings()
        );
        when(deterministicService.build(scope(), "dev1", "zt001")).thenReturn(large);

        var result = service.preview(request(RuntimeConfigurationVerificationMode.BASIC));

        assertThat(result.mapping().documents().get(0).root().children()).hasSize(5_000);
        assertThat(result.artifacts())
                .filteredOn(artifact -> artifact.name().contains("/manifest/"))
                .singleElement()
                .extracting(artifact -> artifact.truncated())
                .isEqualTo(true);
        assertThat(result.visibilityLimits())
                .anyMatch(limit -> limit.contains("was truncated for the AI context"));
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
