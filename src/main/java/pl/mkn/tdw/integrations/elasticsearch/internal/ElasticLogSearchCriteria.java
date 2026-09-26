package pl.mkn.tdw.integrations.elasticsearch.internal;

public record ElasticLogSearchCriteria(
        String kibanaSpaceId,
        String indexPattern,
        String correlationId,
        int size,
        int maxMessageCharacters,
        int maxExceptionCharacters
) {
}
