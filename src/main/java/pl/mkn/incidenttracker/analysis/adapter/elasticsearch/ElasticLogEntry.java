package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

public record ElasticLogEntry(
        String timestamp,
        String level,
        String serviceName,
        String className,
        String message,
        String exception,
        String thread,
        String spanId,
        String namespace,
        String podName,
        String containerName,
        String containerImage,
        String indexName,
        String documentId,
        boolean messageTruncated,
        boolean exceptionTruncated
) {
}
