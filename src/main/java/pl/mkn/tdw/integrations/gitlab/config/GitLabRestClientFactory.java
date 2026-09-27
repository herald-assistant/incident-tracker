package pl.mkn.tdw.integrations.gitlab.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.support.http.IntegrationRestClientBuilderFactory;

@Component
@RequiredArgsConstructor
public class GitLabRestClientFactory {

    private final GitLabProperties properties;
    private final IntegrationRestClientBuilderFactory restClientBuilderFactory;

    public RestClient create() {
        return create(new GitLabConnectionDetails(
                "analysis.gitlab",
                properties.getBaseUrl(),
                properties.getToken()
        ));
    }

    public RestClient create(GitLabConnectionDetails connection) {
        var builder = restClientBuilderFactory.newBuilder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (StringUtils.hasText(connection.token())) {
            builder.defaultHeader("PRIVATE-TOKEN", connection.token());
        }

        return builder.build();
    }
}
