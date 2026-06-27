package pl.mkn.tdw.integrations.elasticsearch;

import java.util.List;

public record ElasticHttpCallLogsResult(
        String correlationId,
        String path,
        Integer status,
        String method,
        int timeWindowDays,
        ElasticLogDetailLevel detailLevel,
        String indexPattern,
        int requestedSize,
        long totalHits,
        int returnedHits,
        int tookMillis,
        boolean timedOut,
        List<ElasticLogEntry> entries,
        String message
) {
}
