package pl.mkn.tdw.api.uiconfig;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.common.PlatformSourceCodeProperties;

import static org.assertj.core.api.Assertions.assertThat;

class UiConfigServiceTest {

    @Test
    void shouldUseDefaultTitleWithoutSubtitleWhenTitleIsNotConfigured() {
        var properties = new UiConfigProperties();
        var service = new UiConfigService(properties, sourceCodeProperties("main"));

        var response = service.currentConfig();

        assertThat(response.title()).isEqualTo("Team Delivery Workspace");
        assertThat(response.subtitle()).isNull();
        assertThat(response.defaultTitle()).isEqualTo("Team Delivery Workspace");
        assertThat(response.defaultBranch()).isEqualTo("main");
    }

    @Test
    void shouldUseConfiguredTitleAndDefaultSubtitleWhenTitleIsConfigured() {
        var properties = new UiConfigProperties();
        properties.setTitle("  CRM Operations Workspace  ");
        var service = new UiConfigService(properties, sourceCodeProperties("crm-release"));

        var response = service.currentConfig();

        assertThat(response.title()).isEqualTo("CRM Operations Workspace");
        assertThat(response.subtitle()).isEqualTo("Team Delivery Workspace");
        assertThat(response.defaultTitle()).isEqualTo("Team Delivery Workspace");
        assertThat(response.defaultBranch()).isEqualTo("crm-release");
    }

    private static PlatformSourceCodeProperties sourceCodeProperties(String defaultBranch) {
        var properties = new PlatformSourceCodeProperties();
        properties.setDefaultBranch(defaultBranch);
        return properties;
    }
}
