package pl.mkn.tdw.features.changeverification.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeAssertionResultResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeDbAssertionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeExecutionRequest;
import pl.mkn.tdw.integrations.database.DatabaseCapabilityDtos;
import pl.mkn.tdw.integrations.database.DatabaseToolService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ChangeVerificationDatabaseReadonlyDbAssertionExecutor implements ChangeVerificationReadonlyDbAssertionExecutor {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private final ObjectProvider<DatabaseToolService> databaseToolServiceProvider;
    private final ChangeVerificationReadonlySqlPolicy readonlySqlPolicy;

    @Override
    public List<ChangeVerificationSmokeAssertionResultResponse> execute(
            List<String> legacyAssertions,
            List<ChangeVerificationSmokeDbAssertionResponse> assertionSpecs,
            ChangeVerificationSmokeExecutionRequest request
    ) {
        var assertions = normalizedAssertions(legacyAssertions, assertionSpecs);
        return assertions.stream()
                .map(assertion -> executeAssertion(assertion, request))
                .toList();
    }

    private ChangeVerificationSmokeAssertionResultResponse executeAssertion(
            ChangeVerificationSmokeDbAssertionResponse assertion,
            ChangeVerificationSmokeExecutionRequest request
    ) {
        var sql = replaceVariables(assertion.sql(), request.variables());
        var policy = readonlySqlPolicy.validate(sql);
        if (!policy.allowed()) {
            return result(assertion, "BLOCKED_BY_POLICY", policy.message());
        }
        if (!StringUtils.hasText(request.environment())) {
            return result(assertion, "READY_FOR_READONLY_EXECUTION",
                    "Readonly SQL accepted, but execution requires environment.");
        }

        var databaseToolService = databaseToolServiceProvider.getIfAvailable();
        if (databaseToolService == null) {
            return result(assertion, "SKIPPED",
                    "Database integration is disabled; readonly assertion was not executed.");
        }

        try {
            var dbResult = databaseToolService.executeReadonlySql(scope(request), new DatabaseCapabilityDtos.DbReadonlySqlRequest(
                    sql,
                    reason(assertion, request),
                    5
            ));
            return evaluate(assertion, dbResult.rows().size(), dbResult.truncated(), dbResult.warnings());
        } catch (RuntimeException exception) {
            return result(assertion, "FAILED", "Readonly DB assertion failed: " + safeMessage(exception));
        }
    }

    private ChangeVerificationSmokeAssertionResultResponse evaluate(
            ChangeVerificationSmokeDbAssertionResponse assertion,
            int rowCount,
            boolean truncated,
            List<String> warnings
    ) {
        var operator = value(assertion.operator(), "").toUpperCase(Locale.ROOT);
        var expected = parseInt(assertion.expectedValue());
        var passed = switch (operator) {
            case "EXISTS", "ROW_COUNT_GT_0" -> rowCount > 0;
            case "NOT_EXISTS", "ROW_COUNT_EQ_0" -> rowCount == 0;
            case "ROW_COUNT_EQ" -> expected != null && rowCount == expected;
            case "ROW_COUNT_GT" -> expected != null && rowCount > expected;
            case "ROW_COUNT_GTE" -> expected != null && rowCount >= expected;
            case "ROW_COUNT_LT" -> expected != null && rowCount < expected;
            case "ROW_COUNT_LTE" -> expected != null && rowCount <= expected;
            default -> null;
        };
        var message = "Readonly SQL executed; rows=%d%s%s.".formatted(
                rowCount,
                truncated ? ", truncated=true" : "",
                warnings.isEmpty() ? "" : ", warnings=" + warnings
        );
        if (passed == null) {
            return result(assertion, "NEEDS_MANUAL_REVIEW", message);
        }
        return result(assertion, passed ? "PASSED" : "FAILED",
                message + " Expected %s %s.".formatted(operator, value(assertion.expectedValue(), "")));
    }

    private List<ChangeVerificationSmokeDbAssertionResponse> normalizedAssertions(
            List<String> legacyAssertions,
            List<ChangeVerificationSmokeDbAssertionResponse> assertionSpecs
    ) {
        var assertions = new ArrayList<ChangeVerificationSmokeDbAssertionResponse>();
        if (assertionSpecs != null) {
            assertions.addAll(assertionSpecs);
        }
        var index = 1;
        for (var legacy : legacyAssertions != null ? legacyAssertions : List.<String>of()) {
            assertions.add(new ChangeVerificationSmokeDbAssertionResponse(
                    "legacy-db-%03d".formatted(index++),
                    legacy,
                    null,
                    null,
                    "Legacy DB assertion"
            ));
        }
        return assertions.stream()
                .filter(assertion -> assertion != null && StringUtils.hasText(assertion.sql()))
                .toList();
    }

    private DatabaseCapabilityDtos.DbCapabilityScope scope(ChangeVerificationSmokeExecutionRequest request) {
        return new DatabaseCapabilityDtos.DbCapabilityScope(
                null,
                request.environment(),
                null,
                null,
                null,
                "change_verification_smoke_db_assertion"
        );
    }

    private String reason(
            ChangeVerificationSmokeDbAssertionResponse assertion,
            ChangeVerificationSmokeExecutionRequest request
    ) {
        var reason = StringUtils.hasText(assertion.description())
                ? assertion.description().trim()
                : "Change Verification smoke DB assertion";
        if (StringUtils.hasText(request.databaseApplication())) {
            reason += " for application " + request.databaseApplication();
        }
        return reason;
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

    private ChangeVerificationSmokeAssertionResultResponse result(
            ChangeVerificationSmokeDbAssertionResponse assertion,
            String status,
            String message
    ) {
        return new ChangeVerificationSmokeAssertionResultResponse(
                "DB_READONLY",
                value(assertion.id(), assertion.sql()),
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

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
