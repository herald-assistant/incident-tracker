package pl.mkn.tdw.integrations.elasticsearch.contract;

public record ElasticHttpStatusBucket(
        String status,
        long returnedCount
) {
}
