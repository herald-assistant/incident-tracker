package pl.mkn.tdw.integrations.jira;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.jira")
public class JiraProperties {

    private String baseUrl;
    private String token;
    private List<String> acceptanceCriteriaFieldIds = new ArrayList<>();
    private int maxComments = 20;
    private int maxRemoteLinks = 50;
    private int maxTextCharacters = 12_000;
}
