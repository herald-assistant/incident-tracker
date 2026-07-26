package pl.mkn.tdw.features.changeverification.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationNameValueResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeAssertionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeAssertionResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeCleanupResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeExecutionRequest;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeHttpResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokePackResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeTestExecutionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeTestResponse;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChangeVerificationSmokeExecutionService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private final RestClient.Builder restClientBuilder;
    private final ChangeVerificationExecutionProperties properties;

    public List<ChangeVerificationSmokeTestExecutionResponse> execute(
            ChangeVerificationSmokePackResponse smokePack,
            ChangeVerificationSmokeExecutionRequest request
    ) {
        var selectedTestIds = request.selectedTestIds();
        return smokePack.tests().stream()
                .filter(test -> selectedTestIds.isEmpty() || selectedTestIds.contains(test.id()))
                .map(test -> executeTest(test, request))
                .toList();
    }

    private ChangeVerificationSmokeTestExecutionResponse executeTest(
            ChangeVerificationSmokeTestResponse test,
            ChangeVerificationSmokeExecutionRequest request
    ) {
        if (!"READY".equalsIgnoreCase(value(test.reviewStatus(), "NEEDS_REVIEW"))) {
            return skipped(test, "NEEDS_REVIEW", "Test requires manual review before execution.");
        }

        var http = executeHttp(test, request);
        var responseAssertions = responseAssertions(test.responseAssertions(), http);
        var dbAssertions = List.<ChangeVerificationSmokeAssertionResultResponse>of();
        var cleanup = cleanupResult(test, request);
        var status = testStatus(http, responseAssertions, cleanup);

        return new ChangeVerificationSmokeTestExecutionResponse(
                test.id(),
                test.name(),
                status,
                http,
                responseAssertions,
                dbAssertions,
                cleanup
        );
    }

    private ChangeVerificationSmokeTestExecutionResponse skipped(
            ChangeVerificationSmokeTestResponse test,
            String status,
            String message
    ) {
        return new ChangeVerificationSmokeTestExecutionResponse(
                test.id(),
                test.name(),
                status,
                null,
                List.of(new ChangeVerificationSmokeAssertionResultResponse("POLICY", "reviewStatus", status, message)),
                List.of(),
                new ChangeVerificationSmokeCleanupResultResponse("NONE", "SKIPPED", null, null, "Test was not executed.")
        );
    }

    private ChangeVerificationSmokeHttpResultResponse executeHttp(
            ChangeVerificationSmokeTestResponse test,
            ChangeVerificationSmokeExecutionRequest request
    ) {
        var method = value(test.method(), "GET").toUpperCase(Locale.ROOT);
        var url = requestUrl(test, request);
        var startedAt = Instant.now();
        try {
            var response = send(test, request, method, url);
            return new ChangeVerificationSmokeHttpResultResponse(
                    method,
                    url,
                    response.getStatusCode().value(),
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    excerpt(response.getBody()),
                    responseHeaders(response),
                    null
            );
        } catch (RestClientResponseException exception) {
            return new ChangeVerificationSmokeHttpResultResponse(
                    method,
                    url,
                    exception.getStatusCode().value(),
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    excerpt(exception.getResponseBodyAsString()),
                    List.of(),
                    null
            );
        } catch (RuntimeException exception) {
            return new ChangeVerificationSmokeHttpResultResponse(
                    method,
                    url,
                    null,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    null,
                    List.of(),
                    exception.getMessage()
            );
        }
    }

    private ResponseEntity<String> send(
            ChangeVerificationSmokeTestResponse test,
            ChangeVerificationSmokeExecutionRequest request,
            String method,
            String url
    ) {
        var spec = restClientBuilder.build()
                .method(HttpMethod.valueOf(method))
                .uri(URI.create(url));
        for (var header : test.headers()) {
            if (header.enabled() && StringUtils.hasText(header.name())) {
                spec.header(header.name(), replaceVariables(header.value(), request.variables()));
            }
        }
        if (StringUtils.hasText(test.requestBody())) {
            return spec.body(replaceVariables(test.requestBody(), request.variables()))
                    .retrieve()
                    .toEntity(String.class);
        }
        return spec.retrieve().toEntity(String.class);
    }

    private List<ChangeVerificationNameValueResponse> responseHeaders(ResponseEntity<String> response) {
        return response.getHeaders().entrySet().stream()
                .limit(20)
                .map(entry -> new ChangeVerificationNameValueResponse(
                        entry.getKey(),
                        String.join(",", entry.getValue()),
                        true
                ))
                .toList();
    }

    private List<ChangeVerificationSmokeAssertionResultResponse> responseAssertions(
            List<ChangeVerificationSmokeAssertionResponse> assertions,
            ChangeVerificationSmokeHttpResultResponse http
    ) {
        if (assertions == null || assertions.isEmpty()) {
            return List.of(new ChangeVerificationSmokeAssertionResultResponse(
                    "STATUS",
                    "status",
                    http.statusCode() != null && http.statusCode() < 500 ? "PASSED" : "FAILED",
                    "Default status sanity check."
            ));
        }

        return assertions.stream()
                .map(assertion -> responseAssertion(assertion, http))
                .toList();
    }

    private ChangeVerificationSmokeAssertionResultResponse responseAssertion(
            ChangeVerificationSmokeAssertionResponse assertion,
            ChangeVerificationSmokeHttpResultResponse http
    ) {
        var type = value(assertion.type(), "").toUpperCase(Locale.ROOT);
        if (http.errorMessage() != null) {
            return assertionResult(assertion, "FAILED", "HTTP request failed: " + http.errorMessage());
        }
        if ("STATUS".equals(type)) {
            var expected = parseInt(assertion.expectedValue());
            var passed = expected != null && expected.equals(http.statusCode());
            return assertionResult(assertion, passed ? "PASSED" : "FAILED",
                    "Expected HTTP status %s, got %s.".formatted(assertion.expectedValue(), http.statusCode()));
        }
        if ("HEADER".equals(type)) {
            var found = http.headers().stream()
                    .anyMatch(header -> header.name().equalsIgnoreCase(value(assertion.target(), "")));
            return assertionResult(assertion, found ? "PASSED" : "FAILED",
                    "Expected response header %s.".formatted(assertion.target()));
        }
        if ("JSON_PATH".equals(type)) {
            return assertionResult(assertion, "NEEDS_MANUAL_REVIEW",
                    "JSON_PATH assertion is captured for operator review in this execution slice.");
        }
        return assertionResult(assertion, "NEEDS_MANUAL_REVIEW", "Unsupported assertion type for automatic execution.");
    }

    private ChangeVerificationSmokeCleanupResultResponse cleanupResult(
            ChangeVerificationSmokeTestResponse test,
            ChangeVerificationSmokeExecutionRequest request
    ) {
        var cleanup = test.cleanup();
        if (cleanup == null || !StringUtils.hasText(cleanup.strategy()) || "NONE".equalsIgnoreCase(cleanup.strategy())) {
            return new ChangeVerificationSmokeCleanupResultResponse("NONE", "SKIPPED", null, null, "No cleanup requested.");
        }
        if ("MANUAL_SQL".equalsIgnoreCase(cleanup.strategy())) {
            return new ChangeVerificationSmokeCleanupResultResponse(
                    "MANUAL_SQL",
                    "MANUAL_ACTION_REQUIRED",
                    null,
                    cleanup.manualSql(),
                    "Manual SQL fallback is shown to the operator and is never executed by AI."
            );
        }
        if ("ENDPOINT".equalsIgnoreCase(cleanup.strategy())) {
            var action = cleanup.method() + " " + cleanup.path();
            if (!request.executeCleanup()) {
                return new ChangeVerificationSmokeCleanupResultResponse(
                        "ENDPOINT",
                        "SKIPPED",
                        action,
                        null,
                        "Cleanup endpoint execution was not requested."
                );
            }
            if (!cleanupEndpointAllowed(cleanup.path())) {
                return new ChangeVerificationSmokeCleanupResultResponse(
                        "ENDPOINT",
                        "BLOCKED_BY_POLICY",
                        action,
                        null,
                        "Cleanup endpoint is not allowlisted in features.change-verification.execution.cleanup-endpoint-allowlist."
                );
            }
            return executeCleanupEndpoint(test, request, action);
        }
        return new ChangeVerificationSmokeCleanupResultResponse(
                value(cleanup.strategy(), "NEEDS_REVIEW"),
                "NEEDS_REVIEW",
                cleanup.method() + " " + cleanup.path(),
                cleanup.manualSql(),
                "Cleanup requires manual review."
        );
    }

    private String testStatus(
            ChangeVerificationSmokeHttpResultResponse http,
            List<ChangeVerificationSmokeAssertionResultResponse> responseAssertions,
            ChangeVerificationSmokeCleanupResultResponse cleanup
    ) {
        if (http.errorMessage() != null || containsStatus(responseAssertions, "FAILED")
                || "BLOCKED_BY_POLICY".equals(cleanup.status())
                || "FAILED".equals(cleanup.status())) {
            return "FAILED";
        }
        if (containsStatus(responseAssertions, "NEEDS_MANUAL_REVIEW")
                || "MANUAL_ACTION_REQUIRED".equals(cleanup.status())
                || "NEEDS_REVIEW".equals(cleanup.status())) {
            return "PASSED_WITH_WARNINGS";
        }
        return "PASSED";
    }

    private boolean containsStatus(List<ChangeVerificationSmokeAssertionResultResponse> assertions, String status) {
        return assertions.stream().anyMatch(assertion -> status.equals(assertion.status()));
    }

    private boolean cleanupEndpointAllowed(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        return properties.getCleanupEndpointAllowlist().stream()
                .filter(StringUtils::hasText)
                .anyMatch(pattern -> path.matches(pattern.trim()));
    }

    private ChangeVerificationSmokeCleanupResultResponse executeCleanupEndpoint(
            ChangeVerificationSmokeTestResponse test,
            ChangeVerificationSmokeExecutionRequest request,
            String action
    ) {
        var cleanup = test.cleanup();
        try {
            var method = value(cleanup.method(), "POST").toUpperCase(Locale.ROOT);
            var url = stripTrailingSlash(request.baseUrl()) + replaceVariables(normalizePath(cleanup.path()), request.variables());
            var spec = restClientBuilder.build()
                    .method(HttpMethod.valueOf(method))
                    .uri(URI.create(url));
            if (StringUtils.hasText(cleanup.requestBody())) {
                spec.body(replaceVariables(cleanup.requestBody(), request.variables()))
                        .retrieve()
                        .toBodilessEntity();
            } else {
                spec.retrieve().toBodilessEntity();
            }
            return new ChangeVerificationSmokeCleanupResultResponse(
                    "ENDPOINT",
                    "EXECUTED",
                    action,
                    null,
                    "Cleanup endpoint executed successfully."
            );
        } catch (RestClientResponseException exception) {
            return new ChangeVerificationSmokeCleanupResultResponse(
                    "ENDPOINT",
                    "FAILED",
                    action,
                    null,
                    "Cleanup endpoint failed with HTTP status " + exception.getStatusCode().value() + "."
            );
        } catch (RuntimeException exception) {
            return new ChangeVerificationSmokeCleanupResultResponse(
                    "ENDPOINT",
                    "FAILED",
                    action,
                    null,
                    "Cleanup endpoint failed: " + value(exception.getMessage(), exception.getClass().getSimpleName())
            );
        }
    }

    private String requestUrl(ChangeVerificationSmokeTestResponse test, ChangeVerificationSmokeExecutionRequest request) {
        var path = replaceVariables(normalizePath(test.path()), request.variables());
        return stripTrailingSlash(request.baseUrl()) + path + queryString(test.queryParams(), request.variables());
    }

    private String queryString(List<ChangeVerificationNameValueResponse> queryParams, Map<String, String> variables) {
        var enabled = queryParams.stream()
                .filter(ChangeVerificationNameValueResponse::enabled)
                .filter(param -> StringUtils.hasText(param.name()))
                .toList();
        if (enabled.isEmpty()) {
            return "";
        }
        return "?" + enabled.stream()
                .map(param -> param.name() + "=" + replaceVariables(value(param.value(), ""), variables))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String replaceVariables(String value, Map<String, String> variables) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        var matcher = VARIABLE_PATTERN.matcher(value);
        var result = new StringBuffer();
        while (matcher.find()) {
            var variableName = matcher.group(1).trim();
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                    variables.getOrDefault(variableName, matcher.group())
            ));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private ChangeVerificationSmokeAssertionResultResponse assertionResult(
            ChangeVerificationSmokeAssertionResponse assertion,
            String status,
            String message
    ) {
        return new ChangeVerificationSmokeAssertionResultResponse(
                assertion.type(),
                assertion.target(),
                status,
                message
        );
    }

    private Integer parseInt(String value) {
        try {
            return StringUtils.hasText(value) ? Integer.parseInt(value.trim()) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String excerpt(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        var max = Math.max(200, properties.getResponseBodyExcerptCharacters());
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String stripTrailingSlash(String value) {
        return value.replaceFirst("/+$", "");
    }

    private String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
