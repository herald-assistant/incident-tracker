package pl.mkn.incidenttracker.integrations.database;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.database")
public class DatabaseToolProperties {

    private boolean enabled = false;
    private DatabaseConnectionProperties connectionDefaults = new DatabaseConnectionProperties();
    private Map<String, DatabaseConnectionProperties> connections = new LinkedHashMap<>();
    private Map<String, DatabaseApplicationProperties> applications = new LinkedHashMap<>();
    private Map<String, DatabaseEnvironmentProperties> environments = new LinkedHashMap<>();

    private int maxRows = 50;
    private int maxColumns = 40;
    private int maxTablesPerSearch = 30;
    private int maxColumnsPerSearch = 50;
    private int maxTablesToDescribe = 5;
    private int maxResultCharacters = 64_000;
    private Duration queryTimeout = Duration.ofSeconds(5);
    private Duration connectionTimeout = Duration.ofSeconds(5);
    private boolean rawSqlEnabled = false;
    private boolean allowAllSchemas = false;

    public DatabaseEnvironmentProperties resolveEnvironment(String environment) {
        var configured = environments.get(environment);
        if (configured == null) {
            return null;
        }

        var resolved = new DatabaseEnvironmentProperties();
        applyConnectionProperties(resolved, connectionDefaults);

        var connectionName = text(configured.getConnection());
        if (connectionName != null) {
            var connection = connections.get(connectionName);
            if (connection == null) {
                throw new IllegalStateException(
                        "Database environment '%s' references missing connection '%s'."
                                .formatted(environment, connectionName)
                );
            }
            applyConnectionProperties(resolved, connection);
        }

        resolved.setConnection(connectionName);
        resolved.setApplicationUserSuffix(configured.getApplicationUserSuffix());
        if (StringUtils.hasText(configured.getDescription())) {
            resolved.setDescription(configured.getDescription());
        }
        resolved.setAllowedSchemas(copyList(configured.getAllowedSchemas()));
        resolved.setResolvedApplications(resolveApplications(configured.getApplicationUserSuffix()));
        return resolved;
    }

    private Map<String, DatabaseApplicationProperties> resolveApplications(String suffix) {
        var resolved = new LinkedHashMap<String, DatabaseApplicationProperties>();

        for (var entry : applications.entrySet()) {
            resolved.put(entry.getKey(), resolveApplication(entry.getValue(), suffix));
        }

        return resolved;
    }

    private DatabaseApplicationProperties resolveApplication(
            DatabaseApplicationProperties catalogApplication,
            String suffix
    ) {
        var resolved = new DatabaseApplicationProperties();
        resolved.setDatabaseUser(text(catalogApplication.getDatabaseUser()));
        resolved.setSchema(resolveSchema(catalogApplication, suffix));
        resolved.setDescription(text(catalogApplication.getDescription()));
        resolved.setApplicationPatterns(copyList(catalogApplication.getApplicationPatterns()));
        resolved.setRelatedSchemas(copyList(catalogApplication.getRelatedSchemas()));
        return resolved;
    }

    private String resolveSchema(
            DatabaseApplicationProperties catalogApplication,
            String suffix
    ) {
        var explicitSchema = text(catalogApplication.getSchema());
        if (explicitSchema != null) {
            return explicitSchema;
        }

        var databaseUser = text(catalogApplication.getDatabaseUser());
        if (databaseUser == null) {
            return null;
        }
        return databaseUser + (StringUtils.hasText(suffix) ? suffix.trim() : "");
    }

    private void applyConnectionProperties(
            DatabaseEnvironmentProperties target,
            DatabaseConnectionProperties source
    ) {
        if (source == null) {
            return;
        }
        if (StringUtils.hasText(source.getJdbcUrl())) {
            target.setJdbcUrl(source.getJdbcUrl());
        }
        if (StringUtils.hasText(source.getDriverClassName())) {
            target.setDriverClassName(source.getDriverClassName());
        }
        if (StringUtils.hasText(source.getUsername())) {
            target.setUsername(source.getUsername());
        }
        if (StringUtils.hasText(source.getPassword())) {
            target.setPassword(source.getPassword());
        }
        if (StringUtils.hasText(source.getDatabaseAlias())) {
            target.setDatabaseAlias(source.getDatabaseAlias());
        }
        if (StringUtils.hasText(source.getDescription())) {
            target.setDescription(source.getDescription());
        }
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> copyList(List<String> values) {
        var copied = new LinkedHashSet<String>();
        addAllText(copied, values);
        return List.copyOf(copied);
    }

    private void addAllText(LinkedHashSet<String> target, List<String> values) {
        for (var value : safeList(values)) {
            if (StringUtils.hasText(value)) {
                target.add(value.trim());
            }
        }
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }
}
