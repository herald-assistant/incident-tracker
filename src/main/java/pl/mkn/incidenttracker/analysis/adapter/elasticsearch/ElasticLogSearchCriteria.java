package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

public record ElasticLogSearchCriteria(
        String kibanaSpaceId,
        String indexPattern,
        String correlationId,
        int size,
        int maxMessageCharacters,
        int maxExceptionCharacters
) {
}
