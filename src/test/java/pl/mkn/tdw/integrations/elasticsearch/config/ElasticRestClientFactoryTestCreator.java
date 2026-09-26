package pl.mkn.tdw.integrations.elasticsearch.config;

import org.springframework.web.client.RestClient;

public final class ElasticRestClientFactoryTestCreator {

    private ElasticRestClientFactoryTestCreator() {
    }

    public static ElasticRestClientFactory forMockServer(RestClient.Builder restClientBuilder) {
        return ElasticRestClientFactory.forMockServer(restClientBuilder);
    }
}
