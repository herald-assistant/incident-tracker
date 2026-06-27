package pl.mkn.tdw.integrations.elasticsearch;

public record ElasticHttpCallSummaryCriteria(
        String kibanaSpaceId,
        String indexPattern,
        String pathPattern,
        String method,
        String serviceName,
        int timeWindowDays,
        int sampleSize
) {
}
