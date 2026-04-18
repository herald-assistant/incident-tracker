package pl.mkn.incidenttracker.analysis.adapter.elasticsearch;

import lombok.Getter;
import org.springframework.http.HttpStatus;

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
