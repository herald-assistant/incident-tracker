package pl.mkn.incidenttracker.api;

import pl.mkn.incidenttracker.analysis.flow.AnalysisDataNotFoundException;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchException;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogSearchResult;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositorySearchException;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositorySearchResponse;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveException;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveResponse;
import pl.mkn.incidenttracker.analysis.job.AnalysisJobChatUnavailableException;
import pl.mkn.incidenttracker.analysis.job.AnalysisJobNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        var fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .sorted(Comparator.comparing(fieldError -> fieldError.getField()))
                .map(fieldError -> new ApiFieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        var response = new ApiErrorResponse(
                "VALIDATION_ERROR",
                "Request validation failed",
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AnalysisDataNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAnalysisDataNotFound(AnalysisDataNotFoundException exception) {
        var response = new ApiErrorResponse(
                "ANALYSIS_DATA_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(AnalysisJobNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAnalysisJobNotFound(AnalysisJobNotFoundException exception) {
        var response = new ApiErrorResponse(
                "ANALYSIS_JOB_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(AnalysisJobChatUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleAnalysisJobChatUnavailable(
            AnalysisJobChatUnavailableException exception
    ) {
        var response = new ApiErrorResponse(
                exception.code(),
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(GitLabSourceResolveException.class)
    public ResponseEntity<GitLabSourceResolveResponse> handleGitLabSourceResolve(GitLabSourceResolveException exception) {
        return ResponseEntity.status(exception.getStatus()).body(exception.getResponse());
    }

    @ExceptionHandler(ElasticLogSearchException.class)
    public ResponseEntity<ElasticLogSearchResult> handleElasticLogSearch(ElasticLogSearchException exception) {
        return ResponseEntity.status(exception.getStatus()).body(exception.getResponse());
    }

    @ExceptionHandler(GitLabRepositorySearchException.class)
    public ResponseEntity<GitLabRepositorySearchResponse> handleGitLabRepositorySearch(
            GitLabRepositorySearchException exception
    ) {
        return ResponseEntity.status(exception.getStatus()).body(exception.getResponse());
    }

}
