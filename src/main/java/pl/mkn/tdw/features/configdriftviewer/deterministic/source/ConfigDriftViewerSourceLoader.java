package pl.mkn.tdw.features.configdriftviewer.deterministic.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScope;
import pl.mkn.tdw.integrations.gitlab.GitLabExactFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabExactReadError;
import pl.mkn.tdw.integrations.gitlab.GitLabExactReadException;
import pl.mkn.tdw.integrations.gitlab.GitLabExactRepositoryPort;

import java.util.ArrayList;
import java.util.EnumMap;

@Component
@RequiredArgsConstructor
class ConfigDriftViewerSourceLoader {

    private static final int REQUESTED_MAX_CHARACTERS = Integer.MAX_VALUE;

    private final GitLabExactRepositoryPort repositoryPort;

    ConfigDriftViewerRawSnapshotPair load(
            ConfigDriftViewerScope scope,
            String sourceBranch,
            String targetBranch
    ) {
        return new ConfigDriftViewerRawSnapshotPair(
                loadBranch(scope, sourceBranch),
                loadBranch(scope, targetBranch)
        );
    }

    private ConfigDriftViewerRawSnapshot loadBranch(
            ConfigDriftViewerScope scope,
            String branch
    ) {
        try {
            if (!repositoryPort.branchExists(scope.connectionId(), scope.projectPath(), branch)) {
                return branchMissing(branch, scope.configurationDirectory());
            }
        } catch (GitLabExactReadException exception) {
            return branchError(branch, scope.configurationDirectory(), exception.error().name());
        }

        var coverage = new ArrayList<ConfigDriftViewerFileCoverage>();
        var files = new EnumMap<ConfigDriftViewerFileRole, ConfigDriftViewerRawFile>(
                ConfigDriftViewerFileRole.class
        );

        readRequired(
                scope,
                branch,
                ConfigDriftViewerFileRole.GLOBAL_VAR,
                "global.var",
                coverage,
                files
        );
        readRequired(
                scope,
                branch,
                ConfigDriftViewerFileRole.LOCAL_VAR,
                scope.configurationDirectory() + "/local.var",
                coverage,
                files
        );
        readApplication(scope, branch, coverage, files);

        return new ConfigDriftViewerRawSnapshot(
                branch,
                new ConfigDriftViewerBranchCoverage(branch, true, coverage),
                files
        );
    }

    private void readApplication(
            ConfigDriftViewerScope scope,
            String branch,
            ArrayList<ConfigDriftViewerFileCoverage> coverage,
            EnumMap<ConfigDriftViewerFileRole, ConfigDriftViewerRawFile> files
    ) {
        var ymlPath = scope.configurationDirectory() + "/application.yml.kv";
        var yamlPath = scope.configurationDirectory() + "/application.yaml.kv";
        var yml = probeMetadata(scope, branch, ymlPath);
        var yaml = probeMetadata(scope, branch, yamlPath);

        if (yml.metadata() != null && yaml.metadata() != null) {
            coverage.add(coverage(
                    ConfigDriftViewerFileRole.APPLICATION_YAML,
                    ymlPath + " | " + yamlPath,
                    ConfigDriftViewerFileStatus.AMBIGUOUS,
                    null,
                    "BOTH_APPLICATION_YAML_VARIANTS"
            ));
            return;
        }

        if (yml.metadata() == null && yaml.metadata() == null) {
            var error = yml.errorCode() != null ? yml.errorCode() : yaml.errorCode();
            coverage.add(coverage(
                    ConfigDriftViewerFileRole.APPLICATION_YAML,
                    ymlPath + " | " + yamlPath,
                    error == null
                            ? ConfigDriftViewerFileStatus.MISSING
                            : ConfigDriftViewerFileStatus.ERROR,
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
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                selectedPath,
                selectedMetadata,
                coverage,
                files
        );
    }

    private void readRequired(
            ConfigDriftViewerScope scope,
            String branch,
            ConfigDriftViewerFileRole role,
            String path,
            ArrayList<ConfigDriftViewerFileCoverage> coverage,
            EnumMap<ConfigDriftViewerFileRole, ConfigDriftViewerRawFile> files
    ) {
        var probe = probeMetadata(scope, branch, path);
        if (probe.metadata() == null) {
            coverage.add(coverage(
                    role,
                    path,
                    probe.errorCode() == null
                            ? ConfigDriftViewerFileStatus.MISSING
                            : ConfigDriftViewerFileStatus.ERROR,
                    null,
                    probe.errorCode()
            ));
            return;
        }
        readContent(scope, branch, role, path, probe.metadata(), coverage, files);
    }

    private void readContent(
            ConfigDriftViewerScope scope,
            String branch,
            ConfigDriftViewerFileRole role,
            String path,
            GitLabExactFileMetadata metadata,
            ArrayList<ConfigDriftViewerFileCoverage> coverage,
            EnumMap<ConfigDriftViewerFileRole, ConfigDriftViewerRawFile> files
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
                    ? ConfigDriftViewerFileStatus.TRUNCATED
                    : ConfigDriftViewerFileStatus.AVAILABLE;
            coverage.add(coverage(role, path, status, metadata, null));
            files.put(role, new ConfigDriftViewerRawFile(role, path, content.content(), metadata));
        } catch (GitLabExactReadException exception) {
            coverage.add(coverage(
                    role,
                    path,
                    exception.error() == GitLabExactReadError.NOT_FOUND
                            ? ConfigDriftViewerFileStatus.MISSING
                            : ConfigDriftViewerFileStatus.ERROR,
                    metadata,
                    exception.error().name()
            ));
        }
    }

    private MetadataProbe probeMetadata(
            ConfigDriftViewerScope scope,
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

    private ConfigDriftViewerRawSnapshot branchMissing(String branch, String directory) {
        return new ConfigDriftViewerRawSnapshot(
                branch,
                new ConfigDriftViewerBranchCoverage(
                        branch,
                        false,
                        expectedCoverage(directory, ConfigDriftViewerFileStatus.BRANCH_MISSING, "BRANCH_NOT_FOUND")
                ),
                java.util.Map.of()
        );
    }

    private ConfigDriftViewerRawSnapshot branchError(String branch, String directory, String errorCode) {
        return new ConfigDriftViewerRawSnapshot(
                branch,
                new ConfigDriftViewerBranchCoverage(
                        branch,
                        false,
                        expectedCoverage(directory, ConfigDriftViewerFileStatus.ERROR, errorCode)
                ),
                java.util.Map.of()
        );
    }

    private java.util.List<ConfigDriftViewerFileCoverage> expectedCoverage(
            String directory,
            ConfigDriftViewerFileStatus status,
            String errorCode
    ) {
        return java.util.List.of(
                coverage(ConfigDriftViewerFileRole.GLOBAL_VAR, "global.var", status, null, errorCode),
                coverage(ConfigDriftViewerFileRole.LOCAL_VAR, directory + "/local.var", status, null, errorCode),
                coverage(
                        ConfigDriftViewerFileRole.APPLICATION_YAML,
                        directory + "/application.y[a]ml.kv",
                        status,
                        null,
                        errorCode
                )
        );
    }

    private ConfigDriftViewerFileCoverage coverage(
            ConfigDriftViewerFileRole role,
            String path,
            ConfigDriftViewerFileStatus status,
            GitLabExactFileMetadata metadata,
            String errorCode
    ) {
        return new ConfigDriftViewerFileCoverage(
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
