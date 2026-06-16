package pl.mkn.incidenttracker.api.uiconfig;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ui")
public class UiConfigProperties {

    private String title;
}
