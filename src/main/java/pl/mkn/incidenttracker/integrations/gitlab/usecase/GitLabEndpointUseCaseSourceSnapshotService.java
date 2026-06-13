package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryTreeException;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryTreeNode;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryTreeService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class GitLabEndpointUseCaseSourceSnapshotService {

    static final int DEFAULT_MAX_SOURCE_FILES = 500;
    static final int DEFAULT_MAX_FILE_CHARACTERS = 80_000;

    private static final String TREE_CACHE_ATTRIBUTE =
            GitLabEndpointUseCaseSourceSnapshotService.class.getName() + ".repositoryTreeCache";
    private static final Set<String> LOW_VALUE_PATH_TOKENS = Set.of(
            "api",
            "application",
            "adapter",
            "agreement",
            "clp",
            "com",
            "controller",
            "dto",
            "delete",
            "get",
            "id",
            "ids",
            "in",
            "int",
            "java",
            "main",
            "model",
            "models",
            "org",
            "out",
            "pl",
            "post",
            "port",
            "process",
            "put",
            "patch",
            "rest",
            "src",
            "trace",
            "v1",
            "v2",
            "web",
            "webmodel",
            "webmodels"
    );

    private final GitLabProperties gitLabProperties;
    private final GitLabRepositoryTreeService gitLabRepositoryTreeService;
    private final GitLabRepositoryPort gitLabRepositoryPort;
    private final int maxSourceFiles;
    private final int maxFileCharacters;

    @Autowired
    public GitLabEndpointUseCaseSourceSnapshotService(
            GitLabProperties gitLabProperties,
            GitLabRepositoryTreeService gitLabRepositoryTreeService,
            GitLabRepositoryPort gitLabRepositoryPort
    ) {
        this(
                gitLabProperties,
                gitLabRepositoryTreeService,
                gitLabRepositoryPort,
                DEFAULT_MAX_SOURCE_FILES,
                DEFAULT_MAX_FILE_CHARACTERS
        );
    }

    GitLabEndpointUseCaseSourceSnapshotService(
            GitLabProperties gitLabProperties,
            GitLabRepositoryTreeService gitLabRepositoryTreeService,
            GitLabRepositoryPort gitLabRepositoryPort,
            int maxSourceFiles,
            int maxFileCharacters
    ) {
        this.gitLabProperties = gitLabProperties;
        this.gitLabRepositoryTreeService = gitLabRepositoryTreeService;
        this.gitLabRepositoryPort = gitLabRepositoryPort;
        this.maxSourceFiles = Math.max(1, maxSourceFiles);
        this.maxFileCharacters = Math.max(1, maxFileCharacters);
    }

    GitLabEndpointUseCaseSourceSnapshot buildSnapshot(
            String group,
            String branch,
            GitLabEndpointUseCaseContextRequest request
    ) {
        var normalizedGroup = trimSlashes(group);
        var normalizedProjectName = trimSlashes(request != null ? request.projectName() : null);
        var normalizedBranch = branch != null ? branch.trim() : null;
        var sourcePathPrefix = request != null
                ? request.sourcePathPrefix()
                : GitLabEndpointUseCaseContextRequest.DEFAULT_SOURCE_PATH_PREFIX;
        var warnings = new ArrayList<GitLabEndpointUseCaseWarning>();
        warnings.add(branchRefWarning(normalizedBranch));

        if (!StringUtils.hasText(normalizedGroup)
                || !StringUtils.hasText(normalizedProjectName)
                || !StringUtils.hasText(normalizedBranch)) {
            warnings.add(new GitLabEndpointUseCaseWarning(
                    GitLabEndpointUseCaseWarningCodes.SOURCE_SCOPE_MISSING,
                    GitLabEndpointUseCaseWarningSeverity.ERROR,
                    "GitLab source snapshot requires group, projectName and branch.",
                    null,
                    null,
                    List.of()
            ));
            return snapshot(
                    normalizedGroup,
                    normalizedProjectName,
                    normalizedBranch,
                    sourcePathPrefix,
                    GitLabEndpointUseCaseIndexStatus.NOT_BUILT,
                    0,
                    0,
                    false,
                    false,
                    List.of(),
                    warnings
            );
        }

        var treeNodes = fetchTree(normalizedGroup, normalizedProjectName, normalizedBranch, sourcePathPrefix, warnings);
        if (treeNodes == null) {
            return snapshot(
                    normalizedGroup,
                    normalizedProjectName,
                    normalizedBranch,
                    sourcePathPrefix,
                    GitLabEndpointUseCaseIndexStatus.NOT_BUILT,
                    0,
                    0,
                    false,
                    false,
                    List.of(),
                    warnings
            );
        }

        var sourcePaths = sourcePaths(treeNodes, sourcePathPrefix, request);
        var sourceFileLimitReached = sourcePaths.size() > maxSourceFiles;
        if (sourceFileLimitReached) {
            warnings.add(new GitLabEndpointUseCaseWarning(
                    GitLabEndpointUseCaseWarningCodes.SOURCE_FILE_LIMIT_REACHED,
                    GitLabEndpointUseCaseWarningSeverity.WARNING,
                    "Source snapshot limited to " + maxSourceFiles + " Java files out of "
                            + sourcePaths.size() + " eligible files.",
                    null,
                    null,
                    limitedCandidates(sourcePaths.subList(maxSourceFiles, sourcePaths.size()))
            ));
        }

        var selectedPaths = sourcePaths.stream()
                .limit(maxSourceFiles)
                .toList();
        var files = new ArrayList<GitLabEndpointUseCaseSourceFile>();
        var readTruncationDetected = false;
        var readFailureDetected = false;

        for (var sourcePath : selectedPaths) {
            try {
                var content = readFile(normalizedGroup, normalizedProjectName, normalizedBranch, sourcePath);
                files.add(new GitLabEndpointUseCaseSourceFile(
                        sourcePath,
                        content.content(),
                        content.content() != null ? content.content().length() : 0,
                        content.truncated()
                ));
                if (content.truncated()) {
                    readTruncationDetected = true;
                    warnings.add(new GitLabEndpointUseCaseWarning(
                            GitLabEndpointUseCaseWarningCodes.SOURCE_FILE_TRUNCATED,
                            GitLabEndpointUseCaseWarningSeverity.WARNING,
                            "Source file content was truncated at " + maxFileCharacters + " characters.",
                            sourcePath,
                            null,
                            List.of()
                    ));
                }
            } catch (RuntimeException exception) {
                readFailureDetected = true;
                warnings.add(new GitLabEndpointUseCaseWarning(
                        GitLabEndpointUseCaseWarningCodes.SOURCE_FILE_READ_FAILED,
                        GitLabEndpointUseCaseWarningSeverity.ERROR,
                        "GitLab source file read failed: " + exception.getMessage(),
                        sourcePath,
                        null,
                        List.of()
                ));
            }
        }

        var indexStatus = sourceFileLimitReached || readTruncationDetected || readFailureDetected
                ? GitLabEndpointUseCaseIndexStatus.PARTIAL
                : GitLabEndpointUseCaseIndexStatus.BUILT_DURING_CALL;

        log.info(
                "Built GitLab endpoint use case source snapshot group={} projectName={} branch={} files={} eligibleFiles={} status={}",
                normalizedGroup,
                normalizedProjectName,
                normalizedBranch,
                files.size(),
                sourcePaths.size(),
                indexStatus
        );

        return snapshot(
                normalizedGroup,
                normalizedProjectName,
                normalizedBranch,
                sourcePathPrefix,
                indexStatus,
                treeNodes.size(),
                sourcePaths.size(),
                sourceFileLimitReached,
                readTruncationDetected,
                files,
                warnings
        );
    }

    private List<GitLabRepositoryTreeNode> fetchTree(
            String group,
            String projectName,
            String branch,
            String sourcePathPrefix,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        try {
            var session = gitLabRepositoryTreeService.requestScopedSession(TREE_CACHE_ATTRIBUTE);
            return gitLabRepositoryTreeService.fetchRepositoryBlobs(
                    gitLabProperties.getBaseUrl(),
                    group + "/" + projectName,
                    branch,
                    sourcePathPrefix,
                    session
            );
        } catch (GitLabRepositoryTreeException exception) {
            warnings.add(new GitLabEndpointUseCaseWarning(
                    GitLabEndpointUseCaseWarningCodes.SOURCE_TREE_UNAVAILABLE,
                    GitLabEndpointUseCaseWarningSeverity.ERROR,
                    "GitLab repository tree request failed with status " + exception.statusCode() + ".",
                    null,
                    null,
                    List.of()
            ));
            return null;
        }
    }

    private GitLabRepositoryFileContent readFile(
            String group,
            String projectName,
            String branch,
            String sourcePath
    ) {
        return gitLabRepositoryPort.readFile(group, projectName, branch, sourcePath, maxFileCharacters);
    }

    private List<String> sourcePaths(
            List<GitLabRepositoryTreeNode> treeNodes,
            String sourcePathPrefix,
            GitLabEndpointUseCaseContextRequest request
    ) {
        var paths = new LinkedHashSet<String>();
        for (var treeNode : treeNodes != null ? treeNodes : List.<GitLabRepositoryTreeNode>of()) {
            var path = normalizePath(treeNode.path());
            if (isAnalyzableJavaSource(path, sourcePathPrefix)) {
                paths.add(path);
            }
        }

        var priorities = sourcePathPriorities(request);
        var controllerPath = controllerPath(request);
        return paths.stream()
                .sorted(Comparator.comparingInt((String path) -> -sourcePathScore(path, priorities, controllerPath))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private int sourcePathScore(
            String path,
            List<SourcePathPriority> priorities,
            String controllerPath
    ) {
        if (!StringUtils.hasText(path)) {
            return 0;
        }
        if ((priorities == null || priorities.isEmpty()) && !StringUtils.hasText(controllerPath)) {
            return 0;
        }

        var normalizedPath = path.toLowerCase(Locale.ROOT);
        var fileName = fileName(normalizedPath);
        var score = 0;

        if (StringUtils.hasText(controllerPath) && normalizedPath.endsWith(controllerPath)) {
            score += 10_000;
        }
        if (normalizedPath.contains("/adapter/in/rest/")) {
            score += 400;
        }
        if (normalizedPath.contains("/adapter/out/")) {
            score += 320;
        }
        if (normalizedPath.contains("/application/port/")) {
            score += 300;
        }
        if (normalizedPath.contains("/application/service/")) {
            score += 260;
        }
        if (normalizedPath.contains("/model/")) {
            score += 180;
        }
        if (fileName.contains("mapper")) {
            score += 160;
        }
        if (fileName.contains("repository")) {
            score += 140;
        }
        if (fileName.contains("port")) {
            score += 120;
        }

        for (var priority : priorities) {
            var token = priority.token();
            var weight = priority.weight();
            if (containsPathSegment(normalizedPath, token)) {
                score += 160 * weight;
            } else if (fileName.contains(token)) {
                score += 110 * weight;
            } else if (normalizedPath.contains(token)) {
                score += 25 * weight;
            }
        }

        return score;
    }

    private List<SourcePathPriority> sourcePathPriorities(GitLabEndpointUseCaseContextRequest request) {
        var priorities = new java.util.LinkedHashMap<String, Integer>();
        addEndpointPathPriorities(priorities, request != null ? request.endpointPath() : null);
        addControllerPriorities(priorities, controllerClass(request));
        if (request != null) {
            addEndpointPathPriorities(priorities, endpointPathFromEndpointId(request.endpointId()));
        }
        return priorities.entrySet().stream()
                .map(entry -> new SourcePathPriority(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void addEndpointPathPriorities(Map<String, Integer> priorities, String value) {
        var segments = meaningfulPathSegments(value);
        for (int index = 0; index < segments.size(); index++) {
            var weight = index == segments.size() - 1 ? 6 : 2;
            addPriority(priorities, segments.get(index), weight);
        }
    }

    private void addControllerPriorities(Map<String, Integer> priorities, String controllerClass) {
        if (!StringUtils.hasText(controllerClass)) {
            return;
        }
        for (var segment : controllerClass.split("\\.")) {
            addTokenPriorities(priorities, segment, 4);
        }
    }

    private void addTokenPriorities(Map<String, Integer> priorities, String value, int weight) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        var normalized = value.replaceAll("([a-z])([A-Z])", "$1-$2");
        for (var token : normalized.split("[^A-Za-z0-9]+")) {
            addPriority(priorities, token, weight);
        }
    }

    private void addPriority(Map<String, Integer> priorities, String token, int weight) {
        var normalized = normalizeToken(token);
        if (!StringUtils.hasText(normalized) || LOW_VALUE_PATH_TOKENS.contains(normalized)) {
            return;
        }
        priorities.merge(normalized, weight, Math::max);
    }

    private List<String> meaningfulPathSegments(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        var segments = new ArrayList<String>();
        for (var rawSegment : value.split("[/#?&=\\s]+")) {
            if (!StringUtils.hasText(rawSegment) || rawSegment.contains("{") || rawSegment.contains("}")) {
                continue;
            }
            var segment = rawSegment.trim();
            if (segment.contains("->")) {
                continue;
            }
            var normalizedSegment = normalizeToken(segment);
            if (StringUtils.hasText(normalizedSegment) && !LOW_VALUE_PATH_TOKENS.contains(normalizedSegment)) {
                segments.add(normalizedSegment);
            }
        }
        return List.copyOf(segments);
    }

    private String controllerPath(GitLabEndpointUseCaseContextRequest request) {
        var controllerClass = controllerClass(request);
        return StringUtils.hasText(controllerClass)
                ? controllerClass.toLowerCase(Locale.ROOT).replace('.', '/') + ".java"
                : null;
    }

    private String controllerClass(GitLabEndpointUseCaseContextRequest request) {
        if (request == null || !StringUtils.hasText(request.endpointId())) {
            return null;
        }
        var endpointId = request.endpointId();
        var arrowIndex = endpointId.indexOf("->");
        if (arrowIndex < 0) {
            return null;
        }
        var methodSeparator = endpointId.indexOf('#', arrowIndex + 2);
        if (methodSeparator < 0) {
            return null;
        }
        var controllerClass = endpointId.substring(arrowIndex + 2, methodSeparator).trim();
        return StringUtils.hasText(controllerClass) ? controllerClass : null;
    }

    private String endpointPathFromEndpointId(String endpointId) {
        if (!StringUtils.hasText(endpointId)) {
            return null;
        }
        var arrowIndex = endpointId.indexOf("->");
        var leftSide = arrowIndex >= 0 ? endpointId.substring(0, arrowIndex) : endpointId;
        for (var token : leftSide.split("\\s+")) {
            if (token.startsWith("/")) {
                return token;
            }
        }
        return null;
    }

    private boolean containsPathSegment(String path, String segment) {
        return path.equals(segment)
                || path.startsWith(segment + "/")
                || path.endsWith("/" + segment)
                || path.contains("/" + segment + "/");
    }

    private String fileName(String path) {
        var slashIndex = path.lastIndexOf('/');
        return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
    }

    private String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        var normalized = token.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        return normalized.length() >= 3 ? normalized : null;
    }

    private boolean isAnalyzableJavaSource(String path, String sourcePathPrefix) {
        if (!StringUtils.hasText(path) || !path.endsWith(".java")) {
            return false;
        }
        if (StringUtils.hasText(sourcePathPrefix) && !path.startsWith(sourcePathPrefix + "/")) {
            return false;
        }

        var lowerPath = path.toLowerCase(Locale.ROOT);
        return !lowerPath.endsWith("/module-info.java")
                && !lowerPath.endsWith("/package-info.java")
                && !containsSegment(lowerPath, "target")
                && !containsSegment(lowerPath, "build")
                && !containsSegment(lowerPath, "generated")
                && !containsSegment(lowerPath, "generated-sources")
                && !containsPathFragment(lowerPath, "src/test")
                && !containsPathFragment(lowerPath, "src/integrationtest");
    }

    private boolean containsSegment(String path, String segment) {
        return path.equals(segment)
                || path.startsWith(segment + "/")
                || path.endsWith("/" + segment)
                || path.contains("/" + segment + "/");
    }

    private boolean containsPathFragment(String path, String fragment) {
        return path.equals(fragment)
                || path.startsWith(fragment + "/")
                || path.endsWith("/" + fragment)
                || path.contains("/" + fragment + "/");
    }

    private GitLabEndpointUseCaseWarning branchRefWarning(String branch) {
        return new GitLabEndpointUseCaseWarning(
                GitLabEndpointUseCaseWarningCodes.BRANCH_REF_NOT_IMMUTABLE,
                GitLabEndpointUseCaseWarningSeverity.INFO,
                "Source snapshot uses branch ref"
                        + (StringUtils.hasText(branch) ? " '" + branch + "'" : "")
                        + "; results can change until commit SHA support is available.",
                null,
                null,
                List.of()
        );
    }

    private List<String> limitedCandidates(List<String> candidates) {
        return candidates.stream()
                .limit(20)
                .toList();
    }

    private GitLabEndpointUseCaseSourceSnapshot snapshot(
            String group,
            String projectName,
            String branch,
            String sourcePathPrefix,
            GitLabEndpointUseCaseIndexStatus indexStatus,
            int discoveredBlobCount,
            int eligibleSourceFileCount,
            boolean sourceFileLimitReached,
            boolean readTruncationDetected,
            List<GitLabEndpointUseCaseSourceFile> files,
            List<GitLabEndpointUseCaseWarning> warnings
    ) {
        return new GitLabEndpointUseCaseSourceSnapshot(
                group,
                projectName,
                branch,
                sourcePathPrefix,
                indexStatus,
                discoveredBlobCount,
                eligibleSourceFileCount,
                maxSourceFiles,
                maxFileCharacters,
                sourceFileLimitReached,
                readTruncationDetected,
                files,
                warnings
        );
    }

    private String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String trimSlashes(String value) {
        var normalized = normalizePath(value);
        return normalized != null ? normalized : "";
    }

    private record SourcePathPriority(String token, int weight) {
    }
}
