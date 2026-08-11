package pl.mkn.tdw.agenttools.gitlab.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class GitLabApplicationScopeResolver {

    ResolvedApplicationScope resolve(
            List<String> requestedApplicationNames,
            ToolContext toolContext,
            OperationalContextCatalog catalog
    ) {
        var requested = canonicalApplicationNames(requestedApplicationNames, catalog);
        var allowed = canonicalApplicationNames(hiddenAllowedApplicationNames(toolContext), catalog);
        if (requested.isEmpty()) {
            return new ResolvedApplicationScope(allowed, !allowed.isEmpty());
        }

        if (isEmptyCatalog(catalog)) {
            return new ResolvedApplicationScope(requested, false);
        }
        var withoutCodeSearchScope = requested.stream()
                .filter(applicationName -> codeSearchScopes(catalog, List.of(applicationName)).isEmpty())
                .toList();
        if (!withoutCodeSearchScope.isEmpty()) {
            throw new IllegalArgumentException(
                    "applicationNames contains systems without configured code search scope: " + withoutCodeSearchScope
            );
        }
        return new ResolvedApplicationScope(requested, true);
    }

    List<OperationalContextRepositorySearchScope> codeSearchScopes(
            OperationalContextCatalog catalog,
            List<String> applicationNames
    ) {
        var targetIds = applicationScopeTargetIds(applicationNames, catalog);
        if (targetIds.isEmpty()) {
            return List.of();
        }
        return safeList(catalog.codeSearchScopes()).stream()
                .filter(scope -> scopeTargetsApplication(scope, targetIds))
                .toList();
    }

    private List<String> canonicalApplicationNames(
            List<String> applicationNames,
            OperationalContextCatalog catalog
    ) {
        var canonicalNames = new LinkedHashSet<String>();
        for (var applicationName : safeList(applicationNames)) {
            if (!StringUtils.hasText(applicationName)) {
                continue;
            }
            var matchingSystemIds = matchingSystemIds(applicationName, catalog);
            if (matchingSystemIds.isEmpty()) {
                canonicalNames.add(applicationName.trim());
            } else {
                canonicalNames.addAll(matchingSystemIds);
            }
        }
        return List.copyOf(canonicalNames);
    }

    private LinkedHashSet<String> matchingSystemIds(
            String applicationName,
            OperationalContextCatalog catalog
    ) {
        var normalizedApplicationName = normalizeComparable(applicationName);
        var matchingSystemIds = new LinkedHashSet<String>();
        if (!StringUtils.hasText(normalizedApplicationName)) {
            return matchingSystemIds;
        }
        for (var system : safeList(catalog.systems())) {
            if (systemMatches(system, normalizedApplicationName) && StringUtils.hasText(system.id())) {
                matchingSystemIds.add(system.id().trim());
            }
        }
        return matchingSystemIds;
    }

    private ApplicationScopeTargetIds applicationScopeTargetIds(
            List<String> applicationNames,
            OperationalContextCatalog catalog
    ) {
        var systemIds = new LinkedHashSet<String>();
        var processIds = new LinkedHashSet<String>();
        var boundedContextIds = new LinkedHashSet<String>();
        var integrationIds = new LinkedHashSet<String>();

        for (var applicationName : safeList(applicationNames)) {
            var normalizedApplicationName = normalizeComparable(applicationName);
            if (!StringUtils.hasText(normalizedApplicationName)) {
                continue;
            }
            var matched = false;
            for (var system : safeList(catalog.systems())) {
                if (!systemMatches(system, normalizedApplicationName)) {
                    continue;
                }
                matched = true;
                add(systemIds, system.id());
                addAll(processIds, system.references().processes());
                addAll(boundedContextIds, system.references().boundedContexts());
                addAll(integrationIds, system.references().integrations());
            }
            if (!matched) {
                systemIds.add(normalizedApplicationName);
            }
        }
        return new ApplicationScopeTargetIds(systemIds, processIds, boundedContextIds, integrationIds);
    }

    private boolean scopeTargetsApplication(
            OperationalContextRepositorySearchScope scope,
            ApplicationScopeTargetIds targetIds
    ) {
        if (scope.target() == null) {
            return false;
        }
        var targetType = normalizeTargetType(scope.target().type());
        var targetId = normalizeComparable(scope.target().id());
        if (!StringUtils.hasText(targetId)) {
            return false;
        }
        return switch (targetType) {
            case "system" -> targetIds.systemIds().contains(targetId);
            case "process" -> targetIds.processIds().contains(targetId);
            case "bounded_context" -> targetIds.boundedContextIds().contains(targetId);
            case "integration" -> targetIds.integrationIds().contains(targetId);
            default -> false;
        };
    }

    private boolean systemMatches(OperationalContextSystem system, String normalizedApplicationName) {
        var values = new LinkedHashSet<String>();
        add(values, system.id());
        add(values, system.name());
        add(values, system.shortName());
        addAll(values, system.aliases());
        addAll(values, system.genericSignals());
        return values.stream().anyMatch(normalizedApplicationName::equals);
    }

    private List<String> hiddenAllowedApplicationNames(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return List.of();
        }
        var value = toolContext.getContext().get(AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES);
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(item -> item != null && StringUtils.hasText(item.toString()))
                .map(item -> item.toString().trim())
                .toList();
    }

    private boolean isEmptyCatalog(OperationalContextCatalog catalog) {
        return catalog == null
                || (safeList(catalog.systems()).isEmpty()
                && safeList(catalog.codeSearchScopes()).isEmpty());
    }

    private String normalizeTargetType(String value) {
        var normalized = normalizeComparable(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "systems" -> "system";
            case "processes" -> "process";
            case "boundedcontext", "boundedcontexts", "bounded_contexts", "context", "contexts" -> "bounded_context";
            case "integrations" -> "integration";
            default -> normalized;
        };
    }

    private String normalizeComparable(String value) {
        return StringUtils.hasText(value)
                ? value.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace('-', '_')
                        .replaceAll("[^a-z0-9/_]+", "_")
                : null;
    }

    private void add(Set<String> values, String value) {
        var normalized = normalizeComparable(value);
        if (StringUtils.hasText(normalized)) {
            values.add(normalized);
        }
    }

    private void addAll(Set<String> values, List<String> source) {
        safeList(source).forEach(value -> add(values, value));
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    record ResolvedApplicationScope(
            List<String> applicationNames,
            boolean restrictedByApplicationScope
    ) {
        ResolvedApplicationScope {
            applicationNames = applicationNames != null ? List.copyOf(applicationNames) : List.of();
        }
    }

    private record ApplicationScopeTargetIds(
            Set<String> systemIds,
            Set<String> processIds,
            Set<String> boundedContextIds,
            Set<String> integrationIds
    ) {
        private boolean isEmpty() {
            return systemIds.isEmpty()
                    && processIds.isEmpty()
                    && boundedContextIds.isEmpty()
                    && integrationIds.isEmpty();
        }
    }
}
