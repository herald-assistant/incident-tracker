package pl.mkn.tdw.integrations.dynatrace.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.support.http.IntegrationRestClientBuilderFactory;

@Component
@RequiredArgsConstructor
public class DynatraceRestClientFactory {

    private final IntegrationRestClientBuilderFactory restClientBuilderFactory;

    public RestClient create(DynatraceProperties properties) {
        var builder = restClientBuilderFactory.newBuilder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (StringUtils.hasText(properties.getApiToken())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Api-Token " + properties.getApiToken().trim());
        }

        return builder.build();
    }
}
