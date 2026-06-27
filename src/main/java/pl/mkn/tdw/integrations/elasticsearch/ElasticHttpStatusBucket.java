package pl.mkn.tdw.integrations.elasticsearch;

public record ElasticHttpStatusBucket(
        String status,
        long returnedCount
) {
}
