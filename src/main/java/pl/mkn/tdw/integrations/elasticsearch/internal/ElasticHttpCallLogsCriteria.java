package pl.mkn.tdw.integrations.elasticsearch.internal;

import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticLogDetailLevel;

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
