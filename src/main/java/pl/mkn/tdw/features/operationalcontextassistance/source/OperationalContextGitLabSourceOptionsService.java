package pl.mkn.tdw.features.operationalcontextassistance.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.common.GitLabPathUtils;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceSourceOptions;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGit;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OperationalContextGitLabSourceOptionsService {

    private static final Pattern SAFE_PROJECT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,511}");

    private final GitLabProperties gitLabProperties;
    private final OperationalContextPort operationalContextPort;

    public OperationalContextAssistanceSourceOptions getOptions() {
        var configuredBaseUrl = normalizeBaseUrl(gitLabProperties.getBaseUrl());
        var configuredGroup = normalizeGroup(gitLabProperties.getGroup());
        if (configuredGroup == null) {
            return new OperationalContextAssistanceSourceOptions(configuredBaseUrl, null, List.of());
        }

        var catalog = operationalContextPort.loadContext(new OperationalContextQuery(
                Set.of(OperationalContextEntryType.REPOSITORY), List.of()
        ));
        var projects = new LinkedHashMap<String, OperationalContextAssistanceSourceOptions.Project>();
        for (var repository : catalog.repositories()) {
            var git = repository.git();
            if (!isGitLabInConfiguredGroup(configuredGroup, git)) {
                continue;
            }
            var project = relativeProject(configuredGroup, git);
            if (!isSafeProject(project)) {
                continue;
            }
            var projectPath = configuredGroup + "/" + project;
            projects.putIfAbsent(project.toLowerCase(Locale.ROOT),
                    new OperationalContextAssistanceSourceOptions.Project(project, projectPath));
        }

        return new OperationalContextAssistanceSourceOptions(
                configuredBaseUrl,
                configuredGroup,
                projects.values().stream()
                        .sorted(Comparator.comparing(
                                OperationalContextAssistanceSourceOptions.Project::projectPath,
                                String.CASE_INSENSITIVE_ORDER
                        ))
                        .toList()
        );
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return null;
        }
        var baseUrl = rawBaseUrl.trim().replaceAll("/+$", "");
        try {
            var uri = URI.create(baseUrl);
            var path = uri.getRawPath();
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || path != null && (path.contains("%") || path.contains("//")
                    || path.contains("/./") || path.contains("/../")
                    || path.endsWith("/.") || path.endsWith("/.."))) {
                return null;
            }
            return baseUrl;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeGroup(String group) {
        if (!StringUtils.hasText(group)) {
            return null;
        }
        var normalized = GitLabPathUtils.trimSlashes(group.trim());
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private boolean isGitLabInConfiguredGroup(String configuredGroup, OperationalContextGit git) {
        if (!"gitlab".equals(git.provider())) {
            return false;
        }
        if (StringUtils.hasText(git.group())) {
            return GitLabPathUtils.isSameOrNestedPath(configuredGroup, git.group());
        }
        if (!StringUtils.hasText(git.projectPath())) {
            return true;
        }
        var projectPath = GitLabPathUtils.trimSlashes(git.projectPath().trim());
        // Without an explicit group, only a bare relative name or a path under the configured group is unambiguous.
        return !projectPath.contains("/")
                || projectPath.regionMatches(true, 0, configuredGroup + "/", 0, configuredGroup.length() + 1);
    }

    private String relativeProject(String configuredGroup, OperationalContextGit git) {
        var rawPath = git.projectPath();
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }
        var projectPath = GitLabPathUtils.trimSlashes(rawPath.trim());
        var relative = GitLabPathUtils.relativeProjectPath(configuredGroup, projectPath);
        if (!relative.equals(projectPath) || !StringUtils.hasText(git.group())) {
            return relative;
        }

        var repositoryGroup = normalizeGroup(git.group());
        if (repositoryGroup == null || repositoryGroup.equalsIgnoreCase(configuredGroup)) {
            return relative;
        }
        var subgroup = GitLabPathUtils.relativeProjectPath(configuredGroup, repositoryGroup);
        return GitLabPathUtils.isSameOrNestedPath(subgroup, relative)
                ? relative
                : subgroup + "/" + relative;
    }

    private boolean isSafeProject(String project) {
        return StringUtils.hasText(project)
                && SAFE_PROJECT.matcher(project).matches()
                && !project.endsWith("/")
                && !project.contains("//")
                && !project.contains("..")
                && !project.contains("/./")
                && !project.endsWith("/.")
                && !project.contains("@{");
    }
}
