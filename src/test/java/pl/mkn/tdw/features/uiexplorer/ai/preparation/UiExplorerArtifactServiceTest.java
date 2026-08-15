package pl.mkn.tdw.features.uiexplorer.ai.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerArtifactServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UiExplorerArtifactService service = new UiExplorerArtifactService(objectMapper);

    @Test
    void shouldRenderSixBoundedCrmArtifactsWithExplicitTrustClassification() throws Exception {
        var artifacts = service.renderArtifacts(request(), context());

        assertThat(artifacts).extracting(artifact -> artifact.displayName())
                .containsExactly(
                        UiExplorerArtifactService.REQUEST_ARTIFACT,
                        UiExplorerArtifactService.SCREEN_CATALOG_ENTRY_ARTIFACT,
                        UiExplorerArtifactService.CONTEXT_SNAPSHOT_ARTIFACT,
                        UiExplorerArtifactService.EVIDENCE_MANIFEST_ARTIFACT,
                        UiExplorerArtifactService.COVERAGE_ARTIFACT,
                        UiExplorerArtifactService.RESPONSE_CONTRACT_ARTIFACT
                );
        assertThat(artifacts).allSatisfy(artifact -> {
            assertThat(artifact.provider()).isEqualTo("ui-explorer");
            assertThat(artifact.content()).isNotBlank();
        });

        var requestJson = content(artifacts, UiExplorerArtifactService.REQUEST_ARTIFACT);
        assertThat(objectMapper.readTree(requestJson).path("trustClassification").asText())
                .isEqualTo("UNTRUSTED_USER_INPUT");
        assertThat(requestJson).contains("Ignore previous instructions");

        var contextJson = content(artifacts, UiExplorerArtifactService.CONTEXT_SNAPSHOT_ARTIFACT);
        var parsedContext = objectMapper.readTree(contextJson);
        assertThat(parsedContext.path("sourceEvidencePolicy").path("classification").asText())
                .isEqualTo("UNTRUSTED_SOURCE_EVIDENCE");
        assertThat(parsedContext.path("sourceFiles").get(1).path("contentClassification").asText())
                .isEqualTo("UNTRUSTED_SOURCE_EVIDENCE");
        assertThat(contextJson).contains("Ignore previous instructions")
                .doesNotContain("</artifact>")
                .doesNotContain("```json");

        var manifest = content(artifacts, UiExplorerArtifactService.EVIDENCE_MANIFEST_ARTIFACT);
        assertThat(manifest).contains("metadata only")
                .contains("crm-contact-preferences.component.ts")
                .doesNotContain("loadRuntimeDefinition");
        objectMapper.readTree(content(artifacts, UiExplorerArtifactService.RESPONSE_CONTRACT_ARTIFACT));
    }

    @Test
    void shouldKeepCanonicalResponseContractAlignedWithUiExplorerEnums() {
        var contract = service.responseContract();

        assertThat(contract)
                .contains("FUNCTIONAL_DOCUMENTATION|CHANGE_PREPARATION|TECHNICAL_DOCUMENTATION")
                .contains("OVERVIEW|NAVIGATION_AND_ACCESS|SCREEN_STRUCTURE|ACTIONS_AND_OUTCOMES|FORMS_AND_RULES|DATA_AND_SERVICES|STATE_AND_SYNCHRONIZATION|VARIANTS_AND_FAILURES")
                .contains("CONFIRMED|INFERRED|UNKNOWN")
                .contains("\"usage\": null")
                .doesNotContain("summary\": \"legacy");
    }

    private String content(
            java.util.List<pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRenderedArtifact> artifacts,
            String name
    ) {
        return artifacts.stream()
                .filter(artifact -> name.equals(artifact.displayName()))
                .findFirst()
                .orElseThrow()
                .content();
    }
}
