package pl.mkn.tdw.features.uiexplorer.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.crmCatalogWithUnknownSubtype;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.crmCatalogWithoutPrimary;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.crmCatalogWithoutProjectPath;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.eligibleCrmCatalog;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.port;

class UiExplorerFrontendCatalogServiceTest {

    @Test
    void shouldExposeOnlyCompletelyRegisteredCrmFrontend() {
        var catalog = new UiExplorerFrontendCatalogService(port(eligibleCrmCatalog())).loadCatalog();

        assertThat(catalog.contentDigest()).isEqualTo("crm-catalog-digest");
        assertThat(catalog.frontends()).singleElement().satisfies(frontend -> {
            assertThat(frontend.systemId()).isEqualTo("crm-agent-portal");
            assertThat(frontend.repositoryId()).isEqualTo("crm-agent-portal-repository");
            assertThat(frontend.projectPath()).isEqualTo("crm/agent-portal");
            assertThat(frontend.gitLabGroup()).isEqualTo("crm");
            assertThat(frontend.gitLabProjectName()).isEqualTo("agent-portal");
            assertThat(frontend.defaultBranch()).isEqualTo("main");
            assertThat(frontend.searchMode()).isEqualTo("whole-repository");
        });
        assertThat(catalog.configurationFindings()).isEmpty();
    }

    @Test
    void shouldOmitFrontendWithoutPrimaryRepositoryAndReturnFinding() {
        var catalog = new UiExplorerFrontendCatalogService(port(crmCatalogWithoutPrimary())).loadCatalog();

        assertThat(catalog.frontends()).isEmpty();
        assertThat(catalog.configurationFindings())
                .extracting(UiExplorerConfigurationFinding::code)
                .contains("FRONTEND_SCOPE_WITHOUT_PRIMARY_REPOSITORY");
    }

    @Test
    void shouldOmitUnknownSubtypeAndReturnFinding() {
        var catalog = new UiExplorerFrontendCatalogService(port(crmCatalogWithUnknownSubtype())).loadCatalog();

        assertThat(catalog.frontends()).isEmpty();
        assertThat(catalog.configurationFindings())
                .extracting(UiExplorerConfigurationFinding::code)
                .contains("INTERNAL_SERVICE_SUBTYPE_UNKNOWN");
    }

    @Test
    void shouldRequireCanonicalProjectPath() {
        var catalog = new UiExplorerFrontendCatalogService(port(crmCatalogWithoutProjectPath())).loadCatalog();

        assertThat(catalog.frontends()).isEmpty();
        assertThat(catalog.configurationFindings())
                .extracting(UiExplorerConfigurationFinding::code)
                .contains("FRONTEND_PRIMARY_REPOSITORY_PROJECT_PATH_REQUIRED");
    }
}
