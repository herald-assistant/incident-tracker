package pl.mkn.incidenttracker.analysis.adapter.database;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.mcp.database.DatabaseToolDtos.DbTableRef;
import pl.mkn.incidenttracker.analysis.mcp.database.DatabaseToolDtos.DbToolScope;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseSqlGuard {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Z][A-Z0-9_$#]*");
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

    private final DatabaseToolProperties properties;
    private final DatabaseApplicationScopeResolver scopeResolver;
    private final DatabaseMetadataClient metadataClient;

    public String normalizeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Oracle identifier must not be blank.");
        }

        var normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!IDENTIFIER_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Oracle identifier '%s' is not allowed. Only simple unquoted identifiers are supported."
                            .formatted(value)
            );
        }
        return normalized;
    }

    public DbTableRef normalizeTableRef(DbToolScope scope, DbTableRef table) {
        if (table == null) {
            throw new IllegalArgumentException("DbTableRef must not be null.");
        }

        var normalizedSchema = normalizeIdentifier(table.schema());
        var normalizedTableName = normalizeIdentifier(table.tableName());
        assertSchemaAllowed(scope, normalizedSchema);

        if (!metadataClient.tableExists(scope.environment(), normalizedSchema, normalizedTableName)) {
            throw new IllegalArgumentException(
                    "Oracle table/view %s.%s does not exist or is not visible in the configured scope."
                            .formatted(normalizedSchema, normalizedTableName)
            );
        }

        return new DbTableRef(normalizedSchema, normalizedTableName);
    }

    public String validateColumn(DbToolScope scope, DbTableRef table, String column) {
        var normalizedTable = normalizeTableRef(scope, table);
        var normalizedColumn = normalizeIdentifier(column);
        if (!metadataClient.columnExists(
                scope.environment(),
                normalizedTable.schema(),
                normalizedTable.tableName(),
                normalizedColumn
        )) {
            throw new IllegalArgumentException(
                    "Oracle column %s.%s.%s does not exist or is not visible in the configured scope."
                            .formatted(normalizedTable.schema(), normalizedTable.tableName(), normalizedColumn)
            );
        }
        return normalizedColumn;
    }

    public List<String> validateColumns(DbToolScope scope, DbTableRef table, List<String> columns) {
        var validated = new LinkedHashSet<String>();
        for (var column : columns != null ? columns : List.<String>of()) {
            validated.add(validateColumn(scope, table, column));
        }
        return List.copyOf(validated);
    }

    public void assertSchemaAllowed(DbToolScope scope, String schema) {
        var normalizedSchema = normalizeIdentifier(schema);
        if (properties.isAllowAllSchemas()) {
            return;
        }

        var environmentProperties = scopeResolver.requiredEnvironment(scope.environment());
        var allowedSchemas = new LinkedHashSet<>(scopeResolver.allowedSchemas(environmentProperties));

        if (!allowedSchemas.contains(normalizedSchema)) {
            throw new IllegalArgumentException(
                    "Oracle schema '%s' is outside the configured allowlist for environment '%s'."
                            .formatted(normalizedSchema, scope.environment())
            );
        }
    }

    public String validateReadonlySql(DbToolScope scope, String sql) {
        if (!properties.isRawSqlEnabled()) {
            throw new IllegalStateException("Raw read-only SQL is disabled for database tools.");
        }
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("Readonly SQL must not be blank.");
        }

        var normalized = sql.trim();
        var upper = normalized.toUpperCase(Locale.ROOT);

        if (normalized.contains(";")) {
            throw new IllegalArgumentException("Readonly SQL must not contain ';'.");
        }
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
            throw new IllegalArgumentException("Readonly SQL must start with SELECT or WITH.");
        }
        for (var keyword : FORBIDDEN_SQL_KEYWORDS) {
            if (upper.contains(keyword)) {
                throw new IllegalArgumentException(
                        "Readonly SQL contains forbidden keyword '%s'.".formatted(keyword)
                );
            }
        }

        return normalized;
    }
}
