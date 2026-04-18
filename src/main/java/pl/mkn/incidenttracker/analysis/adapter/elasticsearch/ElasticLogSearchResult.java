package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

import java.util.List;

public record ElasticLogSearchResult(
        String correlationId,
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
