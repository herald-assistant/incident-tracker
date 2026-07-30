package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;
import pl.mkn.tdw.integrations.gitlab.GitLabExactFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabExactReadError;
import pl.mkn.tdw.integrations.gitlab.GitLabExactReadException;
import pl.mkn.tdw.integrations.gitlab.GitLabExactRepositoryPort;

import java.util.ArrayList;
import java.util.EnumMap;

@Component
@RequiredArgsConstructor
class RuntimeConfigurationSourceLoader {

    private static final int REQUESTED_MAX_CHARACTERS = Integer.MAX_VALUE;

    private final GitLabExactRepositoryPort repositoryPort;

    RuntimeConfigurationRawSnapshotPair load(
            RuntimeConfigurationScope scope,
            String sourceBranch,
            String targetBranch
    ) {
        return new RuntimeConfigurationRawSnapshotPair(
                loadBranch(scope, sourceBranch),
                loadBranch(scope, targetBranch)
        );
    }

    private RuntimeConfigurationRawSnapshot loadBranch(
            RuntimeConfigurationScope scope,
            String branch
    ) {
        try {
            if (!repositoryPort.branchExists(scope.connectionId(), scope.projectPath(), branch)) {
                return branchMissing(branch, scope.configurationDirectory());
            }
        } catch (GitLabExactReadException exception) {
            return branchError(branch, scope.configurationDirectory(), exception.error().name());
        }

        var coverage = new ArrayList<RuntimeConfigurationFileCoverage>();
        var files = new EnumMap<RuntimeConfigurationFileRole, RuntimeConfigurationRawFile>(
                RuntimeConfigurationFileRole.class
        );

        readRequired(
                scope,
                branch,
                RuntimeConfigurationFileRole.GLOBAL_VAR,
                "global.var",
                coverage,
                files
        );
        readRequired(
                scope,
                branch,
                RuntimeConfigurationFileRole.LOCAL_VAR,
                scope.configurationDirectory() + "/local.var",
                coverage,
                files
        );
        readApplication(scope, branch, coverage, files);

        return new RuntimeConfigurationRawSnapshot(
                branch,
                new RuntimeConfigurationBranchCoverage(branch, true, coverage),
                files
        );
    }

    private void readApplication(
            RuntimeConfigurationScope scope,
            String branch,
            ArrayList<RuntimeConfigurationFileCoverage> coverage,
            EnumMap<RuntimeConfigurationFileRole, RuntimeConfigurationRawFile> files
    ) {
        var ymlPath = scope.configurationDirectory() + "/application.yml.kv";
        var yamlPath = scope.configurationDirectory() + "/application.yaml.kv";
        var yml = probeMetadata(scope, branch, ymlPath);
        var yaml = probeMetadata(scope, branch, yamlPath);

        if (yml.metadata() != null && yaml.metadata() != null) {
            coverage.add(coverage(
                    RuntimeConfigurationFileRole.APPLICATION_YAML,
                    ymlPath + " | " + yamlPath,
                    RuntimeConfigurationFileStatus.AMBIGUOUS,
                    null,
                    "BOTH_APPLICATION_YAML_VARIANTS"
            ));
            return;
        }

        if (yml.metadata() == null && yaml.metadata() == null) {
            var error = yml.errorCode() != null ? yml.errorCode() : yaml.errorCode();
            coverage.add(coverage(
                    RuntimeConfigurationFileRole.APPLICATION_YAML,
                    ymlPath + " | " + yamlPath,
                    error == null
                            ? RuntimeConfigurationFileStatus.MISSING
                            : RuntimeConfigurationFileStatus.ERROR,
                    null,
                    error
            ));
            return;
        }

        var selectedPath = yml.metadata() != null ? ymlPath : yamlPath;
        var selectedMetadata = yml.metadata() != null ? yml.metadata() : yaml.metadata();
        readContent(
                scope,
                branch,
                RuntimeConfigurationFileRole.APPLICATION_YAML,
                selectedPath,
                selectedMetadata,
                coverage,
                files
        );
    }

    private void readRequired(
            RuntimeConfigurationScope scope,
            String branch,
            RuntimeConfigurationFileRole role,
            String path,
            ArrayList<RuntimeConfigurationFileCoverage> coverage,
            EnumMap<RuntimeConfigurationFileRole, RuntimeConfigurationRawFile> files
    ) {
        var probe = probeMetadata(scope, branch, path);
        if (probe.metadata() == null) {
            coverage.add(coverage(
                    role,
                    path,
                    probe.errorCode() == null
                            ? RuntimeConfigurationFileStatus.MISSING
                            : RuntimeConfigurationFileStatus.ERROR,
                    null,
                    probe.errorCode()
            ));
            return;
        }
        readContent(scope, branch, role, path, probe.metadata(), coverage, files);
    }

    private void readContent(
            RuntimeConfigurationScope scope,
            String branch,
            RuntimeConfigurationFileRole role,
            String path,
            GitLabExactFileMetadata metadata,
            ArrayList<RuntimeConfigurationFileCoverage> coverage,
            EnumMap<RuntimeConfigurationFileRole, RuntimeConfigurationRawFile> files
    ) {
        try {
            var content = repositoryPort.readFile(
                    scope.connectionId(),
                    scope.projectPath(),
                    branch,
                    path,
                    REQUESTED_MAX_CHARACTERS
            );
            var status = content.truncated()
                    ? RuntimeConfigurationFileStatus.TRUNCATED
                    : RuntimeConfigurationFileStatus.AVAILABLE;
            coverage.add(coverage(role, path, status, metadata, null));
            files.put(role, new RuntimeConfigurationRawFile(role, path, content.content(), metadata));
        } catch (GitLabExactReadException exception) {
            coverage.add(coverage(
                    role,
                    path,
                    exception.error() == GitLabExactReadError.NOT_FOUND
                            ? RuntimeConfigurationFileStatus.MISSING
                            : RuntimeConfigurationFileStatus.ERROR,
                    metadata,
                    exception.error().name()
            ));
        }
    }

    private MetadataProbe probeMetadata(
            RuntimeConfigurationScope scope,
            String branch,
            String path
    ) {
        try {
            return new MetadataProbe(repositoryPort.readFileMetadata(
                    scope.connectionId(),
                    scope.projectPath(),
                    branch,
                    path
            ), null);
        } catch (GitLabExactReadException exception) {
            return exception.error() == GitLabExactReadError.NOT_FOUND
                    ? new MetadataProbe(null, null)
                    : new MetadataProbe(null, exception.error().name());
        }
    }

    private RuntimeConfigurationRawSnapshot branchMissing(String branch, String directory) {
        return new RuntimeConfigurationRawSnapshot(
                branch,
                new RuntimeConfigurationBranchCoverage(
                        branch,
                        false,
                        expectedCoverage(directory, RuntimeConfigurationFileStatus.BRANCH_MISSING, "BRANCH_NOT_FOUND")
                ),
                java.util.Map.of()
        );
    }

    private RuntimeConfigurationRawSnapshot branchError(String branch, String directory, String errorCode) {
        return new RuntimeConfigurationRawSnapshot(
                branch,
                new RuntimeConfigurationBranchCoverage(
                        branch,
                        false,
                        expectedCoverage(directory, RuntimeConfigurationFileStatus.ERROR, errorCode)
                ),
                java.util.Map.of()
        );
    }

    private java.util.List<RuntimeConfigurationFileCoverage> expectedCoverage(
            String directory,
            RuntimeConfigurationFileStatus status,
            String errorCode
    ) {
        return java.util.List.of(
                coverage(RuntimeConfigurationFileRole.GLOBAL_VAR, "global.var", status, null, errorCode),
                coverage(RuntimeConfigurationFileRole.LOCAL_VAR, directory + "/local.var", status, null, errorCode),
                coverage(
                        RuntimeConfigurationFileRole.APPLICATION_YAML,
                        directory + "/application.y[a]ml.kv",
                        status,
                        null,
                        errorCode
                )
        );
    }

    private RuntimeConfigurationFileCoverage coverage(
            RuntimeConfigurationFileRole role,
            String path,
            RuntimeConfigurationFileStatus status,
            GitLabExactFileMetadata metadata,
            String errorCode
    ) {
        return new RuntimeConfigurationFileCoverage(
                role,
                path,
                status,
                metadata != null ? metadata.commitId() : null,
                metadata != null ? metadata.lastCommitId() : null,
                metadata != null ? metadata.lastModifiedAt() : null,
                metadata != null ? metadata.sizeBytes() : null,
                errorCode
        );
    }

    private record MetadataProbe(
            GitLabExactFileMetadata metadata,
            String errorCode
    ) {
    }
}
