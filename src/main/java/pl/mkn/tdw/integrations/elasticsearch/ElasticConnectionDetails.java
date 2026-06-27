package pl.mkn.tdw.integrations.elasticsearch;

public record ElasticConnectionDetails(
        String baseUrl,
        String authorizationHeader
) {
}
