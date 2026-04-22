package pl.mkn.incidenttracker.analysis.adapter.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.mcp.database.DbApplicationScopeInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseApplicationScopeResolver {

    private final DatabaseToolProperties properties;

    public ResolvedDatabaseApplicationScope resolveDiscoveryScope(String environment, String applicationNamePattern) {
        var environmentProperties = requiredEnvironment(environment);
        var warnings = new ArrayList<String>();
        var matchedBecause = new ArrayList<String>();
        var relatedSchemas = new LinkedHashSet<String>();

        if (properties.isAllowAllSchemas()) {
            log.warn(
                    "Database scope is configured with allowAllSchemas=true environment={}",
                    environment
            );
        }

        if (!StringUtils.hasText(applicationNamePattern)) {
            warnings.add("applicationNamePattern was not provided; searched across allowed schemas with strict limit.");
            return new ResolvedDatabaseApplicationScope(
                    environment,
                    environmentProperties.getDatabaseAlias(),
                    null,
                    allowedSchemas(environmentProperties),
                    List.of(),
                    List.of(),
                    List.copyOf(warnings)
            );
        }

        var normalizedPattern = normalizePattern(applicationNamePattern);
        var matches = new ArrayList<ApplicationMatch>();

        for (var entry : environmentProperties.getApplications().entrySet()) {
            var applicationAlias = entry.getKey();
            var applicationProperties = entry.getValue();
            var reasons = matchReasons(applicationAlias, applicationProperties, applicationNamePattern, normalizedPattern);

            if (!reasons.isEmpty()) {
                matches.add(new ApplicationMatch(applicationAlias, applicationProperties, reasons));
            }
        }

        if (matches.isEmpty()) {
            warnings.add(
                    "No configured application mapping matched '%s'; searched across allowed schemas with strict limit."
                            .formatted(applicationNamePattern)
            );
            return new ResolvedDatabaseApplicationScope(
                    environment,
                    environmentProperties.getDatabaseAlias(),
                    null,
                    allowedSchemas(environmentProperties),
                    List.of(),
                    List.of(),
                    List.copyOf(warnings)
            );
        }

        var selectedMatches = matches.size() <= 3 ? matches : matches.subList(0, 3);
        if (matches.size() > 1) {
            warnings.add(
                    "applicationNamePattern '%s' matched multiple configured applications: %s"
                            .formatted(
                                    applicationNamePattern,
                                    matches.stream().map(ApplicationMatch::applicationAlias).toList()
                            )
            );
        }

        var resolvedSchemas = new LinkedHashSet<String>();
        var resolvedAliases = new ArrayList<String>();
        for (var match : selectedMatches) {
            var normalizedSchema = normalizeSchema(match.applicationProperties().getSchema());
            if (normalizedSchema != null) {
                resolvedSchemas.add(normalizedSchema);
            }
            relatedSchemas.addAll(normalizeSchemas(match.applicationProperties().getRelatedSchemas()));
            matchedBecause.addAll(match.reasons());
            resolvedAliases.add(match.applicationAlias());
        }

        return new ResolvedDatabaseApplicationScope(
                environment,
                environmentProperties.getDatabaseAlias(),
                resolvedAliases.size() == 1 ? resolvedAliases.get(0) : String.join(", ", resolvedAliases),
                List.copyOf(resolvedSchemas),
                List.copyOf(relatedSchemas),
                List.copyOf(matchedBecause),
                List.copyOf(warnings)
        );
    }

    public DatabaseEnvironmentProperties requiredEnvironment(String environment) {
        var environmentProperties = properties.getEnvironments().get(environment);
        if (environmentProperties == null) {
            throw new IllegalStateException(
                    "No database environment configuration found for '%s'."
                            .formatted(environment)
            );
        }
        return environmentProperties;
    }

    public List<String> allowedSchemas(DatabaseEnvironmentProperties environmentProperties) {
        var allowedSchemas = new LinkedHashSet<String>();
        allowedSchemas.addAll(normalizeSchemas(environmentProperties.getAllowedSchemas()));

        for (var applicationProperties : environmentProperties.getApplications().values()) {
            var schema = normalizeSchema(applicationProperties.getSchema());
            if (schema != null) {
                allowedSchemas.add(schema);
            }
            allowedSchemas.addAll(normalizeSchemas(applicationProperties.getRelatedSchemas()));
        }

        return List.copyOf(allowedSchemas);
    }

    public List<DbApplicationScopeInfo> describeApplications(String environment) {
        var environmentProperties = requiredEnvironment(environment);
        var applications = new ArrayList<DbApplicationScopeInfo>();

        for (var entry : environmentProperties.getApplications().entrySet()) {
            var application = entry.getValue();
            var patterns = new ArrayList<String>();
            patterns.add(entry.getKey());
            patterns.addAll(safeList(application.getApplicationNamePatterns()));
            patterns.addAll(safeList(application.getDeploymentNamePatterns()));
            patterns.addAll(safeList(application.getContainerNamePatterns()));
            patterns.addAll(safeList(application.getProjectNamePatterns()));

            applications.add(new DbApplicationScopeInfo(
                    entry.getKey(),
                    normalizeSchema(application.getSchema()),
                    normalizeSchemas(application.getRelatedSchemas()),
                    application.getDescription(),
                    deduplicate(patterns)
            ));
        }

        return List.copyOf(applications);
    }

    private List<String> matchReasons(
            String applicationAlias,
            DatabaseApplicationProperties applicationProperties,
            String originalPattern,
            String normalizedPattern
    ) {
        var reasons = new ArrayList<String>();

        if (normalizedPattern.equals(normalizePattern(applicationAlias))) {
            reasons.add("applicationNamePattern matched %s -> %s".formatted(
                    applicationAlias,
                    normalizeSchema(applicationProperties.getSchema())
            ));
        }

        addMatchedReason(reasons, originalPattern, normalizedPattern, applicationProperties.getApplicationNamePatterns(), "applicationNamePatterns");
        addMatchedReason(reasons, originalPattern, normalizedPattern, applicationProperties.getDeploymentNamePatterns(), "deploymentNamePatterns");
        addMatchedReason(reasons, originalPattern, normalizedPattern, applicationProperties.getContainerNamePatterns(), "containerNamePatterns");
        addMatchedReason(reasons, originalPattern, normalizedPattern, applicationProperties.getProjectNamePatterns(), "projectNamePatterns");

        return List.copyOf(reasons);
    }

    private void addMatchedReason(
            List<String> reasons,
            String originalPattern,
            String normalizedPattern,
            List<String> candidates,
            String source
    ) {
        for (var candidate : safeList(candidates)) {
            if (matches(normalizedPattern, candidate)) {
                reasons.add("%s '%s' matched %s".formatted(source, candidate, originalPattern));
            }
        }
    }

    private boolean matches(String normalizedPattern, String configuredValue) {
        var normalizedCandidate = normalizePattern(configuredValue);
        return StringUtils.hasText(normalizedPattern)
                && StringUtils.hasText(normalizedCandidate)
                && (normalizedCandidate.equals(normalizedPattern)
                || normalizedCandidate.contains(normalizedPattern)
                || normalizedPattern.contains(normalizedCandidate));
    }

    private String normalizePattern(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }

    String normalizeSchema(String schema) {
        return StringUtils.hasText(schema) ? schema.trim().toUpperCase(Locale.ROOT) : null;
    }

    List<String> normalizeSchemas(List<String> schemas) {
        var normalized = new ArrayList<String>();
        for (var schema : safeList(schemas)) {
            var value = normalizeSchema(schema);
            if (value != null) {
                normalized.add(value);
            }
        }
        return deduplicate(normalized);
    }

    private List<String> deduplicate(List<String> values) {
        var deduplicated = new LinkedHashSet<String>();
        for (var value : safeList(values)) {
            if (StringUtils.hasText(value)) {
                deduplicated.add(value.trim());
            }
        }
        return List.copyOf(deduplicated);
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private record ApplicationMatch(
            String applicationAlias,
            DatabaseApplicationProperties applicationProperties,
            List<String> reasons
    ) {
    }
}
