package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

public record ElasticConnectionDetails(
        String baseUrl,
        String authorizationHeader
) {
}
