package pl.mkn.tdw.testsupport.integrations;

import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.support.http.IntegrationHttpProperties;
import pl.mkn.tdw.integrations.support.http.IntegrationRestClientBuilderFactory;

public final class IntegrationRestClientBuilderFactoryTestCreator {

    private IntegrationRestClientBuilderFactoryTestCreator() {
    }

    public static IntegrationRestClientBuilderFactory create(RestClient.Builder builder) {
        return create(builder, false);
    }

    public static IntegrationRestClientBuilderFactory create(RestClient.Builder builder, boolean ignoreSslErrors) {
        var properties = new IntegrationHttpProperties();
        properties.setIgnoreSslErrors(ignoreSslErrors);
        return new IntegrationRestClientBuilderFactory(properties, builder);
    }
}
