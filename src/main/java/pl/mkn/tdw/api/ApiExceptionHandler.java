package pl.mkn.tdw.api;

import pl.mkn.tdw.api.database.DatabaseToolApiException;
import pl.mkn.tdw.api.operationalcontext.OperationalContextEntityNotFoundException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotLocalTokenMissingException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotAuthRequiredException;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.GitHubCopilotReauthRequiredException;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallDiagnosticError;
import pl.mkn.tdw.integrations.elasticsearch.ElasticHttpCallSearchException;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogSearchException;
import pl.mkn.tdw.integrations.elasticsearch.ElasticLogSearchResult;
import pl.mkn.tdw.integrations.github.auth.GitHubOAuthExchangeException;
import pl.mkn.tdw.integrations.github.auth.GitHubOAuthStateInvalidException;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchException;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchResponse;
import pl.mkn.tdw.integrations.gitlab.source.GitLabSourceResolveException;
import pl.mkn.tdw.integrations.gitlab.source.GitLabSourceResolveResponse;
import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;
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

    @ExceptionHandler(UserFacingApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleUserFacingApplication(UserFacingApplicationException exception) {
        var response = new ApiErrorResponse(
                exception.code(),
                exception.getMessage(),
                List.of()
        );

        return ResponseEntity.status(toStatus(exception.errorType())).body(response);
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

    private static HttpStatus toStatus(UserFacingErrorType errorType) {
        return switch (errorType) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
