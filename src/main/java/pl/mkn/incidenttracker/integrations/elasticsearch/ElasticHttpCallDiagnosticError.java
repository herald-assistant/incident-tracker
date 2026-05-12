package pl.mkn.incidenttracker.integrations.elasticsearch;

public record ElasticHttpCallDiagnosticError(
        String operation,
        String indexPattern,
        String message
) {
}
