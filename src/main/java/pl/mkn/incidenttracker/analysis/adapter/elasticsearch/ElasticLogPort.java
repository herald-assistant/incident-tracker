package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

import java.util.List;

public interface ElasticLogPort {

    List<ElasticLogEntry> findLogEntries(String correlationId);

    ElasticLogSearchResult searchLogsByCorrelationId(String correlationId);

}
