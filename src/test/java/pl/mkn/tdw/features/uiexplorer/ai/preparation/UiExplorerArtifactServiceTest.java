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
    void shouldRenderSevenReachabilityCrmArtifactsWithBusinessWritingContract() throws Exception {
        var artifacts = service.renderArtifacts(request(), context());

        assertThat(artifacts).extracting(artifact -> artifact.displayName())
                .containsExactly(
                        UiExplorerArtifactService.REQUEST_ARTIFACT,
                        UiExplorerArtifactService.SCREEN_CATALOG_ENTRY_ARTIFACT,
                        UiExplorerArtifactService.REACHABILITY_OUTLINE_ARTIFACT,
                        UiExplorerArtifactService.SOURCE_SLICES_ARTIFACT,
                        UiExplorerArtifactService.COVERAGE_ARTIFACT,
                        UiExplorerArtifactService.FUNCTIONAL_WRITING_CONTRACT_ARTIFACT,
                        UiExplorerArtifactService.REPORT_CONTRACT_ARTIFACT
                );
        assertThat(artifacts).allSatisfy(artifact -> {
            assertThat(artifact.provider()).isEqualTo("ui-explorer");
            assertThat(artifact.content()).isNotBlank();
        });

        var requestJson = content(artifacts, UiExplorerArtifactService.REQUEST_ARTIFACT);
        assertThat(objectMapper.readTree(requestJson).path("trustClassification").asText())
                .isEqualTo("UNTRUSTED_USER_INPUT");
        assertThat(requestJson).contains("Ignore previous instructions");

        var outline = content(artifacts, UiExplorerArtifactService.REACHABILITY_OUTLINE_ARTIFACT);
        assertThat(outline).contains("Effective route chain", "CrmContactPreferencesComponent")
                .contains("selected screen slice ref: `crm-contact-preferences`")
                .contains("sliceRef=`component-crm-contact-preferences`; initial=EMBEDDED")
                .contains("sliceRef=`dependency-crm-preferences-api`; initial=EMBEDDED")
                .doesNotContain("export class");
        var slices = content(artifacts, UiExplorerArtifactService.SOURCE_SLICES_ARTIFACT);
        assertThat(slices).contains("UNTRUSTED_SOURCE_EVIDENCE")
                .contains("`component-crm-contact-preferences`")
                .contains("`dependency-crm-preferences-api`")
                .contains("crm-contact-preferences.component.ts")
                .contains("crm-contact-preferences.component.html")
                .contains("savePreferences()")
                .contains("CrmContactPreferencesApi")
                .contains("loadDefinition")
                .contains("Ignore previous instructions")
                .doesNotContain("usedBy=", "downstream=", "members=", "entries=", "dependencies=");
        var coverage = objectMapper.readTree(content(artifacts, UiExplorerArtifactService.COVERAGE_ARTIFACT));
        assertThat(coverage.path("researchGaps").get(0).asText()).contains("targeted evidence");
        assertThat(coverage.path("completenessSignals").path("reachableComponentCount").asInt()).isEqualTo(1);
        assertThat(coverage.path("completenessSignals").path("componentTemplateCount").asInt()).isEqualTo(1);
        assertThat(coverage.path("completenessSignals").path("componentTemplateCharacters").asInt()).isPositive();
        assertThat(coverage.path("initialSourceProjection").path("embeddedComponentCount").asInt()).isEqualTo(1);
        assertThat(coverage.path("initialSourceProjection").path("onDemandComponentCount").asInt()).isZero();
        assertThat(content(artifacts, UiExplorerArtifactService.FUNCTIONAL_WRITING_CONTRACT_ARTIFACT))
                .contains("Glowna tresc jest dokumentacja funkcjonalna")
                .contains("Akcja | Kiedy dostepna | Co wykorzystuje | Rezultat | Co widzi uzytkownik")
                .contains("Pole lub grupa | Znaczenie | Wymagalnosc i walidacja")
                .contains("Nie tworz stalej liczby punktow")
                .contains("completenessSignals");
        assertThat(content(artifacts, UiExplorerArtifactService.REPORT_CONTRACT_ARTIFACT))
                .contains("Zrodlem prawdy initial result jest `AnalysisReport`")
                .contains("report_update_header", "report_upsert_section", "report_update_meta", "report_get_current")
                .contains("Nie zwracaj JSON");
    }

    @Test
    void shouldKeepCanonicalReportContractAlignedWithUiExplorerSectionsAndTools() {
        var contract = service.reportContract();

        assertThat(contract)
                .doesNotContain("profile")
                .doesNotContain("changePreparationSummary")
                .contains("OVERVIEW", "NAVIGATION_AND_ACCESS", "SCREEN_STRUCTURE", "ACTIONS_AND_OUTCOMES")
                .contains("FORMS_AND_RULES", "DATA_AND_SERVICES", "STATE_AND_SYNCHRONIZATION", "VARIANTS_AND_FAILURES")
                .contains("report_update_header", "report_upsert_section", "report_update_meta", "report_get_current")
                .contains("high", "medium", "low")
                .doesNotContain("dependencies")
                .doesNotContain("crossSectionDependencies")
                .doesNotContain("fallback JSON");
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
