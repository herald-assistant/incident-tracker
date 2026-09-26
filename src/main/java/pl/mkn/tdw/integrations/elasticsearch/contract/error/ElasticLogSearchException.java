package pl.mkn.tdw.integrations.elasticsearch.contract.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticLogSearchResult;

@Getter
public class ElasticLogSearchException extends RuntimeException {

    private final HttpStatus status;
    private final ElasticLogSearchResult response;

    public ElasticLogSearchException(HttpStatus status, ElasticLogSearchResult response) {
        super(response.message());
        this.status = status;
        this.response = response;
    }

}
