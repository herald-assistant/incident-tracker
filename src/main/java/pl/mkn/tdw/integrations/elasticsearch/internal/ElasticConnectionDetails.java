package pl.mkn.tdw.integrations.elasticsearch.internal;

public record ElasticConnectionDetails(
        String baseUrl,
        String authorizationHeader
) {
}
