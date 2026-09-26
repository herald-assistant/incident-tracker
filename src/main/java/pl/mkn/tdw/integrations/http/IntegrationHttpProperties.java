package pl.mkn.tdw.integrations.http;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "integrations.http")
public class IntegrationHttpProperties {

    private boolean ignoreSslErrors;
}
