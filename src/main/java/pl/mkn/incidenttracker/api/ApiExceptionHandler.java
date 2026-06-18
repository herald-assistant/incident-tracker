package pl.mkn.incidenttracker.api;

import pl.mkn.incidenttracker.features.incidentanalysis.flow.AnalysisDataNotFoundException;
import pl.mkn.incidenttracker.api.database.DatabaseToolApiException;
import pl.mkn.incidenttracker.api.operationalcontext.OperationalContextEntityNotFoundException;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotLocalTokenMissingException;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.GitHubCopilotAuthRequiredException;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.GitHubCopilotReauthRequiredException;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticHttpCallDiagnosticError;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticHttpCallSearchException;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticLogSearchException;
import pl.mkn.incidenttracker.integrations.elasticsearch.ElasticLogSearchResult;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubOAuthExchangeException;
import pl.mkn.incidenttracker.integrations.github.auth.GitHubOAuthStateInvalidException;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchException;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchResponse;
import pl.mkn.incidenttracker.integrations.gitlab.source.GitLabSourceResolveException;
import pl.mkn.incidenttracker.integrations.gitlab.source.GitLabSourceResolveResponse;
import pl.mkn.incidenttracker.features.incidentanalysis.job.error.AnalysisJobChatUnavailableException;
import pl.mkn.incidenttracker.features.incidentanalysis.job.error.AnalysisJobNotFoundException;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerSystemNotFoundException;
import pl.mkn.incidenttracker.features.flowexplorer.endpoint.FlowExplorerGitLabConfigurationException;
import pl.mkn.incidenttracker.features.flowexplorer.job.error.FlowExplorerJobChatUnavailableException;
import pl.mkn.incidenttracker.features.flowexplorer.job.error.FlowExplorerJobNotFoundException;
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

    @ExceptionHandler(FlowExplorerSystemNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleFlowExplorerSystemNotFound(
            FlowExplorerSystemNotFoundException exception
    ) {
        var response = new ApiErrorResponse(
                "FLOW_EXPLORER_SYSTEM_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(FlowExplorerGitLabConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleFlowExplorerGitLabConfiguration(
            FlowExplorerGitLabConfigurationException exception
    ) {
        var response = new ApiErrorResponse(
                "FLOW_EXPLORER_GITLAB_CONFIGURATION_MISSING",
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(FlowExplorerJobNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleFlowExplorerJobNotFound(FlowExplorerJobNotFoundException exception) {
        var response = new ApiErrorResponse(
                "FLOW_EXPLORER_JOB_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(FlowExplorerJobChatUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleFlowExplorerJobChatUnavailable(
            FlowExplorerJobChatUnavailableException exception
    ) {
        var response = new ApiErrorResponse(
                exception.code(),
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(OperationalContextEntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleOperationalContextEntityNotFound(
            OperationalContextEntityNotFoundException exception
    ) {
        var response = new ApiErrorResponse(
                "OPERATIONAL_CONTEXT_ENTITY_NOT_FOUND",
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

    @ExceptionHandler(GitHubCopilotAuthRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleGitHubCopilotAuthRequired(
            GitHubCopilotAuthRequiredException exception
    ) {
        var response = new ApiErrorResponse(
                "GITHUB_COPILOT_AUTH_REQUIRED",
                exception.getMessage(),
                List.of(),
                exception.authStartUrl()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(GitHubCopilotReauthRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleGitHubCopilotReauthRequired(
            GitHubCopilotReauthRequiredException exception
    ) {
        var response = new ApiErrorResponse(
                "GITHUB_COPILOT_REAUTH_REQUIRED",
                exception.getMessage(),
                List.of(),
                exception.authStartUrl()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(CopilotLocalTokenMissingException.class)
    public ResponseEntity<ApiErrorResponse> handleCopilotLocalTokenMissing(CopilotLocalTokenMissingException exception) {
        var response = new ApiErrorResponse(
                "COPILOT_LOCAL_TOKEN_MISSING",
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(GitHubOAuthStateInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handleGitHubOAuthStateInvalid(GitHubOAuthStateInvalidException exception) {
        var response = new ApiErrorResponse(
                "GITHUB_OAUTH_STATE_INVALID",
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(GitHubOAuthExchangeException.class)
    public ResponseEntity<ApiErrorResponse> handleGitHubOAuthExchange(GitHubOAuthExchangeException exception) {
        var response = new ApiErrorResponse(
                "GITHUB_OAUTH_EXCHANGE_FAILED",
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(GitLabSourceResolveException.class)
    public ResponseEntity<GitLabSourceResolveResponse> handleGitLabSourceResolve(GitLabSourceResolveException exception) {
        return ResponseEntity.status(exception.getStatus()).body(exception.getResponse());
    }

    @ExceptionHandler(ElasticLogSearchException.class)
    public ResponseEntity<ElasticLogSearchResult> handleElasticLogSearch(ElasticLogSearchException exception) {
        return ResponseEntity.status(exception.getStatus()).body(exception.getResponse());
    }

    @ExceptionHandler(ElasticHttpCallSearchException.class)
    public ResponseEntity<ElasticHttpCallDiagnosticError> handleElasticHttpCallSearch(
            ElasticHttpCallSearchException exception
    ) {
        return ResponseEntity.status(exception.getStatus()).body(exception.getResponse());
    }

    @ExceptionHandler(GitLabRepositorySearchException.class)
    public ResponseEntity<GitLabRepositorySearchResponse> handleGitLabRepositorySearch(
            GitLabRepositorySearchException exception
    ) {
        return ResponseEntity.status(exception.getStatus()).body(exception.getResponse());
    }

    @ExceptionHandler(DatabaseToolApiException.class)
    public ResponseEntity<ApiErrorResponse> handleDatabaseToolApi(DatabaseToolApiException exception) {
        var response = new ApiErrorResponse(
                exception.code(),
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(exception.status()).body(response);
    }

}
