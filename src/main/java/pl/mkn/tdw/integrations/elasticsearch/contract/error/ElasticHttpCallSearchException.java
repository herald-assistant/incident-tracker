package pl.mkn.tdw.integrations.elasticsearch.contract.error;

import org.springframework.http.HttpStatus;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticHttpCallDiagnosticError;

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
