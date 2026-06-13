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

@Service
@Slf4j
public class GitLabEndpointUseCaseSourceSnapshotService {

    static final int DEFAULT_MAX_SOURCE_FILES = 500;
    static final int DEFAULT_MAX_FILE_CHARACTERS = 80_000;

    private static final String TREE_CACHE_ATTRIBUTE =
            GitLabEndpointUseCaseSourceSnapshotService.class.getName() + ".repositoryTreeCache";

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

        var sourcePaths = sourcePaths(treeNodes, sourcePathPrefix);
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

    private List<String> sourcePaths(List<GitLabRepositoryTreeNode> treeNodes, String sourcePathPrefix) {
        var paths = new LinkedHashSet<String>();
        for (var treeNode : treeNodes != null ? treeNodes : List.<GitLabRepositoryTreeNode>of()) {
            var path = normalizePath(treeNode.path());
            if (isAnalyzableJavaSource(path, sourcePathPrefix)) {
                paths.add(path);
            }
        }

        return paths.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
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
                && !containsSegment(lowerPath, "out")
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
}
