package pl.mkn.incidenttracker.integrations.elasticsearch;

import java.util.List;

public record ElasticHttpCallSummaryResult(
        String pathPattern,
        String method,
        String serviceName,
        int timeWindowDays,
        String indexPattern,
        int requestedSampleSize,
        long totalHits,
        int returnedHits,
        int tookMillis,
        boolean timedOut,
        List<ElasticHttpStatusBucket> statusBuckets,
        List<ElasticHttpCallSample> samples,
        String message
) {
}
