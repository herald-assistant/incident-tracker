package pl.mkn.tdw.integrations.elasticsearch.contract;

public record ElasticHttpCallDiagnosticError(
        String operation,
        String indexPattern,
        String message
) {
}
