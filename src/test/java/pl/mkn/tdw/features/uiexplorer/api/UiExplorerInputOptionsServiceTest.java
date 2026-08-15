package pl.mkn.tdw.features.uiexplorer.api;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerFrontendCatalogService;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.eligibleCrmCatalog;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.port;

class UiExplorerInputOptionsServiceTest {

    @Test
    void shouldReturnCrmFrontendAndCompleteUiExplorerContractOptions() {
        var catalogService = new UiExplorerFrontendCatalogService(port(eligibleCrmCatalog()));
        var response = new UiExplorerInputOptionsService(catalogService).inputOptions();

        assertThat(response.featureId()).isEqualTo("ui-explorer");
        assertThat(response.executionAvailability().status().name()).isEqualTo("AVAILABLE");
        assertThat(response.executionAvailability().code()).isEqualTo("UI_EXPLORER_ANALYSIS_AVAILABLE");
        assertThat(response.executionAvailability().missingCapabilities()).isEmpty();
        assertThat(response.systems()).singleElement().satisfies(system -> {
            assertThat(system.systemId()).isEqualTo("crm-agent-portal");
            assertThat(system.label()).isEqualTo("CRM Agent Portal");
        });
        assertThat(response.profiles()).hasSize(3);
        assertThat(response.profiles()).allSatisfy(profile ->
                assertThat(profile.defaultSectionModes()).hasSize(8));
        assertThat(response.sections()).hasSize(8);
        assertThat(response.modes()).hasSize(3);
        assertThat(response.configurationFindings()).isEmpty();
    }
}
