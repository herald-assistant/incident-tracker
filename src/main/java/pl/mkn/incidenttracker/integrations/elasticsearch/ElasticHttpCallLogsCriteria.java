package pl.mkn.incidenttracker.integrations.elasticsearch;

public record ElasticHttpCallLogsCriteria(
        String kibanaSpaceId,
        String indexPattern,
        String correlationId,
        String path,
        Integer status,
        String method,
        int timeWindowDays,
        int size,
        int maxMessageCharacters,
        int maxExceptionCharacters,
        ElasticLogDetailLevel detailLevel
) {
}
