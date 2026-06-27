package pl.mkn.tdw.integrations.elasticsearch;

public record ElasticHttpCallDiagnosticError(
        String operation,
        String indexPattern,
        String message
) {
}
