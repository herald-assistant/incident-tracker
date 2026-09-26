package pl.mkn.tdw.integrations.confluence;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.http.IntegrationRestClientBuilderFactory;

@Component
@RequiredArgsConstructor
public class ConfluenceRestClientFactory {

    private final ConfluenceProperties properties;
    private final IntegrationRestClientBuilderFactory restClientBuilderFactory;

    public RestClient create() {
        var builder = restClientBuilderFactory.newBuilder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (StringUtils.hasText(properties.getToken())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken().trim());
        }

        return builder.build();
    }
}
