package pl.mkn.incidenttracker.integrations.elasticsearch;

public record ElasticConnectionDetails(
        String baseUrl,
        String authorizationHeader
) {
}
