package pl.mkn.incidenttracker.integrations.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.database.DatabaseCapabilityDtos.DbCapabilityScope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseConnectionRouter implements AutoCloseable {

    private static final Pattern ORACLE_THIN_SID_URL = Pattern.compile(
            "jdbc:oracle:thin:@([^/:]+):(\\d+):([^:]+)"
    );

    private final DatabaseToolProperties properties;
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, NamedParameterJdbcTemplate> namedTemplates = new ConcurrentHashMap<>();

    public DatabaseEnvironmentHandle route(DbCapabilityScope scope) {
        return route(scope.environment());
    }

    public DatabaseEnvironmentHandle route(String environment) {
        var environmentKey = requireEnvironment(environment);
        var environmentProperties = requiredEnvironmentProperties(environmentKey);
        var namedParameterJdbcTemplate = namedTemplates.computeIfAbsent(
                environmentKey,
                ignored -> createNamedTemplate(environmentKey, environmentProperties)
        );
        return new DatabaseEnvironmentHandle(
                environmentKey,
                environmentProperties.getDatabaseAlias(),
                environmentProperties.getDescription(),
                namedParameterJdbcTemplate,
                namedParameterJdbcTemplate.getJdbcTemplate()
        );
    }

    DatabaseEnvironmentProperties requiredEnvironmentProperties(String environment) {
        var environmentProperties = properties.resolveEnvironment(environment);
        if (environmentProperties == null) {
            throw new IllegalStateException(
                    "No database environment configuration found for '%s'."
                            .formatted(environment)
            );
        }
        return environmentProperties;
    }

    private NamedParameterJdbcTemplate createNamedTemplate(
            String environment,
            DatabaseEnvironmentProperties environmentProperties
    ) {
        var dataSource = dataSources.computeIfAbsent(
                environment,
                ignored -> createDataSource(environment, environmentProperties)
        );
        var jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout((int) properties.getQueryTimeout().getSeconds());
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private HikariDataSource createDataSource(
            String environment,
            DatabaseEnvironmentProperties environmentProperties
    ) {
        if (!StringUtils.hasText(environmentProperties.getJdbcUrl())
                || !StringUtils.hasText(environmentProperties.getUsername())
                || !StringUtils.hasText(environmentProperties.getPassword())) {
            throw new IllegalStateException(
                    "Database environment '%s' is enabled but missing jdbcUrl/username/password configuration."
                            .formatted(environment)
            );
        }

        var jdbcUrl = environmentProperties.getJdbcUrl().strip();
        var username = environmentProperties.getUsername().strip();
        var driverClassName = resolveDriverClassName(environmentProperties, jdbcUrl);

        log.info(
                "Initializing read-only database pool environment={} databaseAlias={} driverClassName={}",
                environment,
                environmentProperties.getDatabaseAlias(),
                StringUtils.hasText(driverClassName) ? driverClassName : "<auto>"
        );

        try {
            var configuration = new HikariConfig();
            configuration.setJdbcUrl(jdbcUrl);
            configuration.setUsername(username);
            configuration.setPassword(environmentProperties.getPassword());
            if (StringUtils.hasText(driverClassName)) {
                configuration.setDriverClassName(driverClassName);
            }
            configuration.setPoolName("incident-db-" + environment);
            configuration.setMaximumPoolSize(3);
            configuration.setReadOnly(true);
            configuration.setConnectionTimeout(properties.getConnectionTimeout().toMillis());
            return new HikariDataSource(configuration);
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException(
                    buildDataSourceInitializationMessage(environment, jdbcUrl, driverClassName, exception),
                    exception
            );
        }
    }

    String resolveDriverClassName(DatabaseEnvironmentProperties environmentProperties) {
        return resolveDriverClassName(environmentProperties, environmentProperties.getJdbcUrl());
    }

    String resolveDriverClassName(
            DatabaseEnvironmentProperties environmentProperties,
            String jdbcUrl
    ) {
        if (StringUtils.hasText(environmentProperties.getDriverClassName())) {
            return environmentProperties.getDriverClassName().strip();
        }
        if (StringUtils.hasText(jdbcUrl) && jdbcUrl.strip().startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        }
        if ("oracle".equalsIgnoreCase(environmentProperties.getDatabaseAlias())) {
            return "oracle.jdbc.OracleDriver";
        }
        return null;
    }

    private String buildDataSourceInitializationMessage(
            String environment,
            String jdbcUrl,
            String driverClassName,
            RuntimeException exception
    ) {
        var driverDetail = StringUtils.hasText(driverClassName)
                ? " with driverClassName '%s'".formatted(driverClassName)
                : "";
        var oracleHint = jdbcUrl.startsWith("jdbc:oracle:")
                ? " For Oracle on Java 17 prefer runtime dependency 'com.oracle.database.jdbc:ojdbc17'."
                : "";
        var serviceNameHint = oracleServiceNameHint(jdbcUrl);
        var rootCause = rootCauseMessage(exception);
        return """
                Failed to initialize database pool for environment '%s' using jdbcUrl '%s'%s.
                Ensure the JDBC driver is present on the runtime classpath and the JDBC URL is valid.%s
                %s
                Root cause: %s.
                You can also configure analysis.database.connections.<connection>.driver-class-name explicitly.
                """
                .formatted(environment, jdbcUrl, driverDetail, oracleHint, serviceNameHint, rootCause)
                .replace('\n', ' ')
                .trim();
    }

    private String rootCauseMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        var message = current.getMessage();
        if (!StringUtils.hasText(message)) {
            message = throwable.getMessage();
        }
        if (!StringUtils.hasText(message)) {
            return current.getClass().getSimpleName();
        }
        return "%s: %s".formatted(current.getClass().getSimpleName(), message.replaceAll("\\s+", " ").trim());
    }

    private String oracleServiceNameHint(String jdbcUrl) {
        var matcher = ORACLE_THIN_SID_URL.matcher(jdbcUrl);
        if (!matcher.matches()) {
            return "";
        }
        return """
                The URL uses Oracle SID-style syntax. If '%s' is a service name, use
                'jdbc:oracle:thin:@//%s:%s/%s' instead.
                """
                .formatted(matcher.group(3), matcher.group(1), matcher.group(2), matcher.group(3))
                .replace('\n', ' ')
                .trim();
    }

    private String requireEnvironment(String environment) {
        if (!StringUtils.hasText(environment)) {
            throw new IllegalArgumentException("Database environment must not be blank.");
        }
        return environment.trim();
    }

    @Override
    public void close() {
        for (var dataSource : dataSources.values()) {
            try {
                dataSource.close();
            }
            catch (RuntimeException exception) {
                log.warn("Failed to close database pool. reason={}", exception.getMessage(), exception);
            }
        }
        dataSources.clear();
        namedTemplates.clear();
    }

    public record DatabaseEnvironmentHandle(
            String environment,
            String databaseAlias,
            String description,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JdbcTemplate jdbcTemplate
    ) {
    }
}
