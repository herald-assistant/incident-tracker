package pl.mkn.tdw.features.changeverification.execution;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Component
public class ChangeVerificationReadonlySqlPolicy {

    private static final List<String> FORBIDDEN_SQL_KEYWORDS = List.of(
            "INSERT",
            "UPDATE",
            "DELETE",
            "MERGE",
            "CREATE",
            "ALTER",
            "DROP",
            "TRUNCATE",
            "GRANT",
            "REVOKE",
            "CALL",
            "EXEC",
            "EXECUTE",
            "COMMIT",
            "ROLLBACK",
            "LOCK"
    );

    public SqlPolicyResult validate(String assertion) {
        if (!StringUtils.hasText(assertion)) {
            return new SqlPolicyResult(false, "Readonly DB assertion is blank.");
        }

        var sql = assertion.trim();
        var upper = sql.toUpperCase(Locale.ROOT);
        if (sql.contains(";")) {
            return new SqlPolicyResult(false, "Readonly SQL must not contain ';'.");
        }
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
            return new SqlPolicyResult(false, "DB assertion is descriptive or not a readonly SQL statement.");
        }
        for (var keyword : FORBIDDEN_SQL_KEYWORDS) {
            if (upper.contains(keyword)) {
                return new SqlPolicyResult(false, "Readonly SQL contains forbidden keyword '%s'.".formatted(keyword));
            }
        }
        return new SqlPolicyResult(true, "Readonly SQL accepted by Change Verification policy.");
    }

    public record SqlPolicyResult(boolean allowed, String message) {
    }
}
