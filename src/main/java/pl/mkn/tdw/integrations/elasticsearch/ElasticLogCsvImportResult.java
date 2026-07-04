package pl.mkn.tdw.integrations.elasticsearch;

import java.util.List;

public record ElasticLogCsvImportResult(
        String correlationId,
        List<ElasticLogEntry> entries,
        int importedRecords
) {

    public ElasticLogCsvImportResult {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }
}
