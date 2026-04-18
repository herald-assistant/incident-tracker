package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GitLabSourceResolveException extends RuntimeException {

    private final HttpStatus status;
    private final GitLabSourceResolveResponse response;

    public GitLabSourceResolveException(HttpStatus status, GitLabSourceResolveResponse response) {
        super(response.message());
        this.status = status;
        this.response = response;
    }

}
