package pl.mkn.incidenttracker.integrations.elasticsearch;

import org.springframework.http.HttpStatus;

public class ElasticHttpCallSearchException extends RuntimeException {

    private final HttpStatus status;
    private final ElasticHttpCallDiagnosticError response;

    public ElasticHttpCallSearchException(HttpStatus status, ElasticHttpCallDiagnosticError response) {
        super(response.message());
        this.status = status;
        this.response = response;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ElasticHttpCallDiagnosticError getResponse() {
        return response;
    }
}
