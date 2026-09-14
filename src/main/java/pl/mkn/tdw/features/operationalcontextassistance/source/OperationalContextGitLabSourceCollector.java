package pl.mkn.tdw.features.operationalcontextassistance.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.common.GitLabPathUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevision;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeExplorer;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeSlice;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OperationalContextGitLabSourceCollector {

    private static final int MAX_FILE_BYTES = 16 * 1024;
    private static final int MAX_INSTRUCTION_FILE_BYTES = 32 * 1024;
    private static final int MAX_TOTAL_BYTES = 96 * 1024;
    private static final String COPILOT_INSTRUCTIONS = ".github/copilot-instructions.md";
    private static final List<String> ALLOWED_FILES = List.of(
            "AGENTS.md", COPILOT_INSTRUCTIONS,
            "README.md", "pom.xml", "package.json", "build.gradle", "settings.gradle"
    );
    private static final Pattern SAFE_PROJECT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,511}");
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}");
    private static final Pattern COMMIT_ID = Pattern.compile("(?:[a-fA-F0-9]{40}|[a-fA-F0-9]{64})");

    private final GitLabProperties properties;
    private final GitLabRepositoryPort repositoryPort;
    private final GitLabRepositoryTreeExplorer treeExplorer;

    public void validateProjectUrl(String projectUrl) {
        relativeProjectFromUrl(projectUrl);
    }

    public String projectPathForBranchOptions(String project, String projectUrl) {
        if (StringUtils.hasText(project) == StringUtils.hasText(projectUrl)) {
            throw invalidProjectUrl("Wybierz jeden projekt z katalogu albo podaj jego pełny adres URL.");
        }
        if (!StringUtils.hasText(properties.getBaseUrl()) || !StringUtils.hasText(properties.getGroup())) {
            throw OperationalContextGitLabSourceSelectionException.missingConfiguration();
        }
        var group = GitLabPathUtils.trimSlashes(properties.getGroup().trim());
        if (!StringUtils.hasText(group)) {
            throw OperationalContextGitLabSourceSelectionException.missingConfiguration();
        }
        if (StringUtils.hasText(projectUrl)) {
            return group + "/" + relativeProjectFromUrl(projectUrl);
        }
        try {
            var normalized = requireSafeRelativePath(project, SAFE_PROJECT, "project");
            return group + "/" + relativeToConfiguredGroup(group, normalized);
        } catch (IllegalArgumentException exception) {
            throw invalidProjectUrl("Wybrany projekt ma nieprawidłową ścieżkę GitLab.");
        }
    }

    public OperationalContextGitLabSourceSnapshot collectUrl(String projectUrl, String ref) {
        return collect(relativeProjectFromUrl(projectUrl), ref);
    }

    public OperationalContextGitLabSourceSnapshot collect(String project, String ref) {
        var normalizedProject = requireSafeRelativePath(project, SAFE_PROJECT, "project");
        var normalizedRef = requireSafeRelativePath(ref, SAFE_REF, "ref");
        var rawGroup = properties.getGroup();
        var group = StringUtils.hasText(rawGroup) ? GitLabPathUtils.trimSlashes(rawGroup.trim()) : null;
        var limits = new ArrayList<String>();
        if (!StringUtils.hasText(group)) {
            limits.add("Nie skonfigurowano grupy GitLab; pominięto wybrane źródło.");
            return snapshot(normalizedProject, null, normalizedRef, null, List.of(), limits);
        }
        normalizedProject = relativeToConfiguredGroup(group, normalizedProject);
        var repositoryGit = repositoryGit(group, normalizedProject);

        GitLabRepositoryRevision revision;
        try {
            revision = repositoryPort.resolveRevision(group, normalizedProject, normalizedRef);
        } catch (RuntimeException exception) {
            limits.add("Nie udało się przypiąć wybranego ref do commita GitLab.");
            return snapshot(normalizedProject, repositoryGit, normalizedRef, null, List.of(), limits);
        }
        if (revision == null
                || !group.equals(revision.group())
                || !normalizedProject.equals(revision.projectName())
                || !normalizedRef.equals(revision.ref())
                || revision.commitId() == null
                || !COMMIT_ID.matcher(revision.commitId()).matches()) {
            limits.add("GitLab nie zwrócił prawidłowego commita dla wybranego projektu i ref.");
            return snapshot(normalizedProject, repositoryGit, normalizedRef, null, List.of(), limits);
        }

        GitLabRepositoryTreeSlice tree;
        boolean treeComplete = false;
        try {
            tree = treeExplorer.explore(group, normalizedProject, revision.commitId(), "", "");
            treeComplete = !tree.truncated();
            if (tree.truncated()) {
                limits.add("Początkowe drzewo GitLab jest częściowe; AI może doczytać katalogi narzędziem.");
            }
        } catch (RuntimeException exception) {
            limits.add("Nie udało się pobrać początkowego drzewa wybranego projektu GitLab.");
            tree = new GitLabRepositoryTreeSlice("", 4, List.of(), List.of(), false);
        }

        var files = new ArrayList<OperationalContextGitLabSourceFile>();
        var totalBytes = 0;
        Set<String> visibleRootFiles = tree.entries().stream()
                .filter(entry -> "blob".equals(entry.type()))
                .map(GitLabRepositoryTreeSlice.Entry::path)
                .collect(java.util.stream.Collectors.toSet());
        for (var path : ALLOWED_FILES) {
            boolean presentInTree = visibleRootFiles.contains(path);
            if (treeComplete && !presentInTree && !COPILOT_INSTRUCTIONS.equals(path)) {
                continue;
            }
            GitLabRepositoryFileMetadata metadata;
            try {
                metadata = repositoryPort.readFileMetadata(group, normalizedProject, revision.commitId(), path);
            } catch (RuntimeException exception) {
                if (presentInTree) {
                    limits.add("Pominięto " + path + ": plik lub jego metadane są niedostępne.");
                }
                continue;
            }
            if (metadata == null && !presentInTree) {
                continue;
            }
            if (!matchesMetadata(metadata, group, normalizedProject, revision.commitId(), path)
                    || metadata.sizeBytes() == null || metadata.sizeBytes() < 0) {
                limits.add("Pominięto " + path + ": brak wiarygodnych metadanych rozmiaru.");
                continue;
            }
            int fileLimit = isInstructionPath(path) ? MAX_INSTRUCTION_FILE_BYTES : MAX_FILE_BYTES;
            if (metadata.sizeBytes() > fileLimit || metadata.sizeBytes() > MAX_TOTAL_BYTES - totalBytes) {
                limits.add("Pominięto " + path + ": plik przekracza limit odczytu.");
                continue;
            }

            GitLabRepositoryFileContent file;
            try {
                file = repositoryPort.readFileBounded(
                        group, normalizedProject, revision.commitId(), path,
                        Math.min(fileLimit, MAX_TOTAL_BYTES - totalBytes)
                );
            } catch (RuntimeException exception) {
                limits.add("Pominięto " + path + ": nie udało się bezpiecznie odczytać pliku w limicie.");
                continue;
            }
            if (!matchesFile(file, group, normalizedProject, revision.commitId(), path)) {
                limits.add("Pominięto " + path + ": odpowiedź nie odpowiada wybranemu commitowi lub jest ucięta.");
                continue;
            }
            var content = file.content();
            var actualBytes = content.getBytes(StandardCharsets.UTF_8).length;
            if (actualBytes > fileLimit || actualBytes > MAX_TOTAL_BYTES - totalBytes
                    || actualBytes != metadata.sizeBytes()
                    || !matchesContentHash(content, metadata.contentSha256())) {
                limits.add("Pominięto " + path + ": treść nie zgadza się z metadanymi lub limitem.");
                continue;
            }
            totalBytes += actualBytes;
            files.add(new OperationalContextGitLabSourceFile(
                    path,
                    content,
                    "gitlab:" + group + "/" + normalizedProject + "@" + revision.commitId() + ":" + path
            ));
        }
        return new OperationalContextGitLabSourceSnapshot(
                normalizedProject, repositoryGit, normalizedRef, revision.commitId(), files, tree, limits);
    }

    private boolean isInstructionPath(String path) {
        return "AGENTS.md".equals(path) || COPILOT_INSTRUCTIONS.equals(path);
    }

    private OperationalContextGitLabSourceSnapshot snapshot(
            String project, OperationalContextGitLabSourceSnapshot.RepositoryGit repositoryGit,
            String ref, String commitId,
            List<OperationalContextGitLabSourceFile> files, List<String> limits
    ) {
        return new OperationalContextGitLabSourceSnapshot(project, repositoryGit, ref, commitId, files, limits);
    }

    private OperationalContextGitLabSourceSnapshot.RepositoryGit repositoryGit(String group, String relativeProject) {
        var projectPath = group + "/" + relativeProject;
        var separator = projectPath.lastIndexOf('/');
        var baseUrl = StringUtils.hasText(properties.getBaseUrl())
                ? properties.getBaseUrl().trim().replaceAll("/+$", "") : null;
        return new OperationalContextGitLabSourceSnapshot.RepositoryGit(
                "gitlab",
                projectPath.substring(0, separator),
                projectPath.substring(separator + 1),
                projectPath,
                baseUrl != null ? baseUrl + "/" + projectPath : null
        );
    }

    private String relativeProjectFromUrl(String projectUrl) {
        if (!StringUtils.hasText(properties.getBaseUrl()) || !StringUtils.hasText(properties.getGroup())) {
            throw OperationalContextGitLabSourceSelectionException.missingConfiguration();
        }
        var base = parseUrl(properties.getBaseUrl(), true);
        var selected = parseUrl(projectUrl, false);
        if (!base.getScheme().equalsIgnoreCase(selected.getScheme())
                || !base.getHost().equalsIgnoreCase(selected.getHost())
                || effectivePort(base) != effectivePort(selected)) {
            throw invalidProjectUrl("Adres projektu nie należy do skonfigurowanego serwera GitLab.");
        }

        var basePath = stripSingleTrailingSlash(base.getRawPath());
        var selectedPath = stripSingleTrailingSlash(selected.getRawPath());
        if (selectedPath == null || selectedPath.isEmpty() || selectedPath.contains("//")) {
            throw invalidProjectUrl("Podaj pełny adres URL strony projektu GitLab.");
        }
        String repositoryPath;
        if (basePath == null || basePath.isEmpty()) {
            repositoryPath = selectedPath.startsWith("/") ? selectedPath.substring(1) : selectedPath;
        } else if (selectedPath.startsWith(basePath + "/")) {
            repositoryPath = selectedPath.substring(basePath.length() + 1);
        } else {
            throw invalidProjectUrl("Adres projektu nie należy do skonfigurowanego adresu bazowego GitLab.");
        }

        var group = GitLabPathUtils.trimSlashes(properties.getGroup().trim());
        var groupPrefix = group + "/";
        if (!repositoryPath.regionMatches(true, 0, groupPrefix, 0, groupPrefix.length())) {
            throw invalidProjectUrl("Projekt musi znajdować się w skonfigurowanej głównej grupie GitLab.");
        }
        var relative = repositoryPath.substring(groupPrefix.length());
        if (relative.endsWith(".git")) {
            relative = relative.substring(0, relative.length() - 4);
        }
        try {
            relative = requireSafeRelativePath(relative, SAFE_PROJECT, "project");
        } catch (IllegalArgumentException exception) {
            throw invalidProjectUrl("Adres URL zawiera nieprawidłową ścieżkę projektu GitLab.");
        }
        if (relative.equals("-") || relative.contains("/-/") || relative.endsWith("/-")) {
            throw invalidProjectUrl("Podaj adres projektu GitLab, bez ścieżki widoku plików lub gałęzi.");
        }
        return relative;
    }

    private URI parseUrl(String value, boolean configuration) {
        try {
            var uri = URI.create(value.trim());
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getRawPath() != null && uri.getRawPath().contains("%")) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException exception) {
            if (configuration) {
                throw OperationalContextGitLabSourceSelectionException.missingConfiguration();
            }
            throw invalidProjectUrl("Podaj pełny adres URL projektu z poprawnym schematem HTTP(S).");
        }
    }

    private int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String stripSingleTrailingSlash(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private OperationalContextGitLabSourceSelectionException invalidProjectUrl(String message) {
        return new OperationalContextGitLabSourceSelectionException(message);
    }

    private String relativeToConfiguredGroup(String group, String project) {
        var prefix = group + "/";
        return project.regionMatches(true, 0, prefix, 0, prefix.length())
                ? requireSafeRelativePath(project.substring(prefix.length()), SAFE_PROJECT, "project")
                : project;
    }

    private String requireSafeRelativePath(String value, Pattern pattern, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required.");
        }
        var normalized = value.trim();
        if (!pattern.matcher(normalized).matches()
                || normalized.endsWith("/")
                || normalized.contains("//")
                || normalized.contains("..")
                || normalized.contains("/./")
                || normalized.endsWith("/.")
                || normalized.contains("@{")) {
            throw new IllegalArgumentException(field + " must be a safe relative GitLab path.");
        }
        return normalized;
    }

    private boolean matchesMetadata(
            GitLabRepositoryFileMetadata metadata, String group, String project, String commit, String path
    ) {
        return metadata != null
                && group.equals(metadata.group())
                && project.equals(metadata.projectName())
                && commit.equals(metadata.branch())
                && path.equals(metadata.filePath())
                && (metadata.commitId() == null || commit.equals(metadata.commitId()));
    }

    private boolean matchesFile(
            GitLabRepositoryFileContent file, String group, String project, String commit, String path
    ) {
        return file != null
                && group.equals(file.group())
                && project.equals(file.projectName())
                && commit.equals(file.branch())
                && path.equals(file.filePath())
                && file.content() != null
                && !file.truncated();
    }

    private boolean matchesContentHash(String content, String expectedHash) {
        if (!StringUtils.hasText(expectedHash)) {
            return true;
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).equals(expectedHash.toLowerCase(Locale.ROOT));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

}
