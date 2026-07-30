package pl.mkn.tdw.features.runtimeconfigurationverification.deep;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.common.GitLabPathUtils;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeRefSource;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflight;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflightBlocker;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflightStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepRepositoryScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeException;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModelBuilder;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextProperties;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextReadModelValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RuntimeConfigurationDeepPreflightService {

    private static final String SYSTEM = "system";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}");

    private final OperationalContextProperties operationalContextProperties;
    private final OperationalContextPort operationalContextPort;
    private final RuntimeConfigurationScopeResolver scopeResolver;
    private final GitLabProperties gitLabProperties;
    private final GitLabRepositoryPort gitLabRepositoryPort;
    private final OperationalContextCodeSearchReadModelBuilder codeSearchBuilder =
            new OperationalContextCodeSearchReadModelBuilder();
    private final OperationalContextReadModelValidator catalogValidator =
            new OperationalContextReadModelValidator();

    public RuntimeConfigurationDeepPreflight check(
            String repositoryId,
            String systemId,
            String codeRef
    ) {
        var blockers = new ArrayList<RuntimeConfigurationDeepPreflightBlocker>();
        var visibilityLimits = new LinkedHashSet<String>();
        if (!validId(repositoryId) || !validId(systemId) || !validRef(codeRef)) {
            addBlocker(
                    blockers,
                    "DEEP_PREFLIGHT_INPUT_INVALID",
                    "DEEP preflight input is invalid."
            );
            return result(repositoryId, systemId, null, List.of(), blockers, visibilityLimits);
        }
        if (!operationalContextProperties.isEnabled()) {
            addBlocker(
                    blockers,
                    "OPERATIONAL_CONTEXT_DISABLED",
                    "Operational Context is disabled."
            );
            return result(repositoryId, systemId, null, List.of(), blockers, visibilityLimits);
        }

        RuntimeConfigurationScope configurationScope;
        try {
            configurationScope = scopeResolver.resolve(repositoryId, systemId);
        } catch (RuntimeConfigurationScopeException exception) {
            addBlocker(blockers, exception.code(), exception.getMessage());
            return result(repositoryId, systemId, null, List.of(), blockers, visibilityLimits);
        } catch (RuntimeException exception) {
            addBlocker(
                    blockers,
                    "OPERATIONAL_CONTEXT_UNAVAILABLE",
                    "Operational Context could not be loaded."
            );
            return result(repositoryId, systemId, null, List.of(), blockers, visibilityLimits);
        }

        OperationalContextCatalog catalog;
        try {
            catalog = operationalContextPort.loadContext(OperationalContextQuery.all());
        } catch (RuntimeException exception) {
            addBlocker(
                    blockers,
                    "OPERATIONAL_CONTEXT_UNAVAILABLE",
                    "Operational Context could not be loaded."
            );
            return result(
                    repositoryId,
                    systemId,
                    configurationScope,
                    List.of(),
                    blockers,
                    visibilityLimits
            );
        }

        for (var finding : catalogValidator.validate(catalog)) {
            if ("error".equalsIgnoreCase(finding.severity())) {
                addBlocker(
                        blockers,
                        "OPERATIONAL_CONTEXT_" + finding.code(),
                        finding.message()
                );
            }
        }

        var codeSearch = codeSearchBuilder.buildForEntity(catalog, SYSTEM, systemId);
        if (codeSearch.scopes().isEmpty() || codeSearch.repositories().isEmpty()) {
            addBlocker(
                    blockers,
                    "DEEP_CODE_SEARCH_SCOPE_MISSING",
                    "The selected internal system has no usable code-search scope."
            );
        }
        if (!blockers.isEmpty()) {
            return result(
                    repositoryId,
                    systemId,
                    configurationScope,
                    List.of(),
                    blockers,
                    visibilityLimits
            );
        }

        if (!codeGitLabConfigured()) {
            addBlocker(
                    blockers,
                    "DEEP_CODE_GITLAB_UNAVAILABLE",
                    "The GitLab connection used for source code is unavailable."
            );
            return result(
                    repositoryId,
                    systemId,
                    configurationScope,
                    List.of(),
                    blockers,
                    visibilityLimits
            );
        }

        var repositories = resolveRepositories(
                codeSearch,
                normalize(codeRef),
                blockers,
                visibilityLimits
        );
        if (!repositories.isEmpty() && repositories.stream().noneMatch(RuntimeConfigurationDeepRepositoryScope::ready)) {
            addBlocker(
                    blockers,
                    "DEEP_CODE_REPOSITORIES_UNAVAILABLE",
                    "No code repository from the selected system scope is ready."
            );
        }

        return result(
                repositoryId,
                systemId,
                configurationScope,
                repositories,
                blockers,
                visibilityLimits
        );
    }

    private List<RuntimeConfigurationDeepRepositoryScope> resolveRepositories(
            OperationalContextCodeSearchReadModel codeSearch,
            String requestedRef,
            List<RuntimeConfigurationDeepPreflightBlocker> blockers,
            LinkedHashSet<String> visibilityLimits
    ) {
        var scopeIdByRepository = new LinkedHashMap<String, String>();
        for (var scope : codeSearch.scopes()) {
            for (var repository : scope.repositories()) {
                scopeIdByRepository.putIfAbsent(repository.id(), scope.scope().id());
            }
        }

        var result = new ArrayList<RuntimeConfigurationDeepRepositoryScope>();
        for (var repository : codeSearch.repositories()) {
            var repositoryLimits = new LinkedHashSet<String>();
            var git = repository.git();
            var projectPath = firstNonBlank(git.projectPath(), git.project());
            var projectName = relativeProjectName(projectPath);
            var structurallyReady = true;

            if (StringUtils.hasText(git.provider())
                    && !"gitlab".equalsIgnoreCase(git.provider())) {
                addBlocker(
                        blockers,
                        "DEEP_CODE_REPOSITORY_PROVIDER_UNSUPPORTED",
                        "Repository " + repository.repository().id() + " is not a GitLab repository."
                );
                structurallyReady = false;
            }
            if (StringUtils.hasText(git.group())
                    && !GitLabPathUtils.isSameOrNestedPath(configuredGroup(), git.group())) {
                addBlocker(
                        blockers,
                        "DEEP_CODE_REPOSITORY_GROUP_MISMATCH",
                        "Repository " + repository.repository().id()
                                + " is outside the configured GitLab group."
                );
                structurallyReady = false;
            }
            if (!StringUtils.hasText(projectName)) {
                addBlocker(
                        blockers,
                        "DEEP_CODE_REPOSITORY_PROJECT_MISSING",
                        "Repository " + repository.repository().id() + " has no GitLab project path."
                );
                structurallyReady = false;
            }

            var selection = structurallyReady
                    ? resolveRef(repository.repository().id(), projectName, requestedRef, git.defaultBranch())
                    : RefSelection.unresolved(requestedRef);
            repositoryLimits.addAll(selection.visibilityLimits());
            visibilityLimits.addAll(selection.visibilityLimits());
            if (!selection.ready()) {
                addBlocker(
                        blockers,
                        "DEEP_CODE_REF_UNRESOLVED",
                        "No usable Git ref could be resolved for repository "
                                + repository.repository().id() + "."
                );
            }

            result.add(new RuntimeConfigurationDeepRepositoryScope(
                    scopeIdByRepository.get(repository.repository().id()),
                    repository.repository().id(),
                    repository.role(),
                    repository.priority(),
                    projectPath,
                    projectName,
                    repository.searchMode(),
                    repository.pathPrefixes(),
                    requestedRef,
                    selection.usedRef(),
                    selection.source(),
                    selection.exists(),
                    false,
                    structurallyReady && selection.ready(),
                    List.copyOf(repositoryLimits)
            ));
        }
        return List.copyOf(result);
    }

    private RefSelection resolveRef(
            String repositoryId,
            String projectName,
            String requestedRef,
            String defaultBranch
    ) {
        var limitations = new LinkedHashSet<String>();
        if (StringUtils.hasText(requestedRef)) {
            var requestedExists = branchExists(projectName, requestedRef);
            if (Boolean.TRUE.equals(requestedExists)) {
                limitations.add("The requested code ref `" + requestedRef
                        + "` exists, but it is not confirmed as the deployed version.");
                return new RefSelection(
                        requestedRef,
                        RuntimeConfigurationCodeRefSource.REQUESTED,
                        true,
                        true,
                        List.copyOf(limitations)
                );
            }
            if (requestedExists == null) {
                limitations.add("GitLab availability could not be checked for repository `"
                        + repositoryId + "` and requested ref `" + requestedRef + "`.");
                return RefSelection.unresolved(requestedRef, List.copyOf(limitations));
            }
            limitations.add("Requested code ref `" + requestedRef
                    + "` does not exist in repository `" + repositoryId + "`.");
        }

        var normalizedDefault = normalize(defaultBranch);
        if (!StringUtils.hasText(normalizedDefault)) {
            limitations.add("Repository `" + repositoryId
                    + "` has no catalog-declared default branch.");
            return RefSelection.unresolved(requestedRef, List.copyOf(limitations));
        }
        var defaultExists = branchExists(projectName, normalizedDefault);
        if (!Boolean.TRUE.equals(defaultExists)) {
            limitations.add("Default branch `" + normalizedDefault
                    + "` could not be confirmed for repository `" + repositoryId + "`.");
            return RefSelection.unresolved(requestedRef, List.copyOf(limitations));
        }
        limitations.add("Code was resolved from default branch `" + normalizedDefault
                + "`; this is not evidence of the deployed version.");
        return new RefSelection(
                normalizedDefault,
                RuntimeConfigurationCodeRefSource.DEFAULT_BRANCH,
                true,
                true,
                List.copyOf(limitations)
        );
    }

    private Boolean branchExists(String projectName, String ref) {
        try {
            return gitLabRepositoryPort.branchExists(configuredGroup(), projectName, ref);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private RuntimeConfigurationDeepPreflight result(
            String repositoryId,
            String systemId,
            RuntimeConfigurationScope scope,
            List<RuntimeConfigurationDeepRepositoryScope> repositories,
            List<RuntimeConfigurationDeepPreflightBlocker> blockers,
            LinkedHashSet<String> visibilityLimits
    ) {
        return new RuntimeConfigurationDeepPreflight(
                blockers.isEmpty()
                        ? RuntimeConfigurationDeepPreflightStatus.READY
                        : RuntimeConfigurationDeepPreflightStatus.BLOCKED,
                normalize(repositoryId),
                normalize(systemId),
                scope != null ? scope.systemLabel() : null,
                scope != null ? scope.configurationDirectory() : null,
                repositories,
                List.copyOf(blockers),
                List.copyOf(visibilityLimits)
        );
    }

    private void addBlocker(
            List<RuntimeConfigurationDeepPreflightBlocker> blockers,
            String code,
            String message
    ) {
        if (blockers.stream().noneMatch(existing -> existing.code().equals(code))) {
            blockers.add(new RuntimeConfigurationDeepPreflightBlocker(code, message));
        }
    }

    private boolean codeGitLabConfigured() {
        return StringUtils.hasText(gitLabProperties.getBaseUrl())
                && StringUtils.hasText(gitLabProperties.getGroup())
                && StringUtils.hasText(gitLabProperties.getToken());
    }

    private String configuredGroup() {
        return GitLabPathUtils.trimSlashes(gitLabProperties.getGroup().trim());
    }

    private String relativeProjectName(String projectPath) {
        return StringUtils.hasText(projectPath)
                ? GitLabPathUtils.relativeProjectPath(configuredGroup(), projectPath)
                : null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : normalize(second);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean validId(String value) {
        return StringUtils.hasText(value) && SAFE_ID.matcher(value.trim()).matches();
    }

    private boolean validRef(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        var normalized = value.trim();
        return SAFE_REF.matcher(normalized).matches()
                && !normalized.contains("..")
                && !normalized.contains("//")
                && !normalized.contains("@{")
                && !normalized.endsWith("/")
                && !normalized.endsWith(".");
    }

    private record RefSelection(
            String usedRef,
            RuntimeConfigurationCodeRefSource source,
            boolean exists,
            boolean ready,
            List<String> visibilityLimits
    ) {

        private RefSelection {
            visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        }

        private static RefSelection unresolved(String requestedRef) {
            return unresolved(requestedRef, List.of());
        }

        private static RefSelection unresolved(String requestedRef, List<String> limits) {
            return new RefSelection(
                    null,
                    RuntimeConfigurationCodeRefSource.UNRESOLVED,
                    false,
                    false,
                    limits
            );
        }
    }
}
