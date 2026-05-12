package pl.mkn.incidenttracker.integrations.elasticsearch;

public record ElasticHttpCallSample(
        String timestamp,
        String method,
        String path,
        Integer status,
        String correlationId,
        String serviceName,
        String className,
        String message,
        String indexName,
        String documentId
) {
}
