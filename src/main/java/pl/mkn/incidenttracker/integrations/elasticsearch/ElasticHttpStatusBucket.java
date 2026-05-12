package pl.mkn.incidenttracker.integrations.elasticsearch;

public record ElasticHttpStatusBucket(
        String status,
        long returnedCount
) {
}
