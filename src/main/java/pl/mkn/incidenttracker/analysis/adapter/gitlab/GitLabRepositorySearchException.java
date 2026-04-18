package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GitLabRepositorySearchException extends RuntimeException {

    private final HttpStatus status;
    private final GitLabRepositorySearchResponse response;

    public GitLabRepositorySearchException(HttpStatus status, GitLabRepositorySearchResponse response) {
        super(response.message());
        this.status = status;
        this.response = response;
    }

}
