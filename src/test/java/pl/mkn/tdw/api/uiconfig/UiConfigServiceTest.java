package pl.mkn.tdw.api.uiconfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiConfigServiceTest {

    @Test
    void shouldUseDefaultTitleWithoutSubtitleWhenTitleIsNotConfigured() {
        var properties = new UiConfigProperties();
        var service = new UiConfigService(properties);

        var response = service.currentConfig();

        assertThat(response.title()).isEqualTo("Team Delivery Workspace");
        assertThat(response.subtitle()).isNull();
        assertThat(response.defaultTitle()).isEqualTo("Team Delivery Workspace");
    }

    @Test
    void shouldUseConfiguredTitleAndDefaultSubtitleWhenTitleIsConfigured() {
        var properties = new UiConfigProperties();
        properties.setTitle("  Acme Engineering Workspace  ");
        var service = new UiConfigService(properties);

        var response = service.currentConfig();

        assertThat(response.title()).isEqualTo("Acme Engineering Workspace");
        assertThat(response.subtitle()).isEqualTo("Team Delivery Workspace");
        assertThat(response.defaultTitle()).isEqualTo("Team Delivery Workspace");
    }
}
