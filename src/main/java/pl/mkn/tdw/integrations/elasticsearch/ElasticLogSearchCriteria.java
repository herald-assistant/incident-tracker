package pl.mkn.tdw.integrations.elasticsearch;

public record ElasticLogSearchCriteria(
        String kibanaSpaceId,
        String indexPattern,
        String correlationId,
        int size,
        int maxMessageCharacters,
        int maxExceptionCharacters
) {
}
