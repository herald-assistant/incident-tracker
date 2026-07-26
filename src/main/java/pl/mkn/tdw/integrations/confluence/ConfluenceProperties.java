package pl.mkn.tdw.integrations.confluence;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.confluence")
public class ConfluenceProperties {

    private String baseUrl;
    private String token;
    private String urlPattern;
    private int maxTextCharacters = 20_000;
}
