package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;
import pl.mkn.tdw.integrations.gitlab.GitLabExactFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabExactFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabExactReadException;
import pl.mkn.tdw.integrations.gitlab.GitLabExactRepositoryPort;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigurationSourceLoaderTest {

    @Test
    void shouldLoadBothApplicationFileNamesWithMetadata() {
        var repository = new FakeRepository();
        repository.branch("dev1").branch("zt001");
        repository.file("dev1", "global.var", "locals {}");
        repository.file("dev1", "backend/local.var", "locals {}");
        repository.file("dev1", "backend/application.yml.kv", "feature: true");
        repository.file("zt001", "global.var", "locals {}");
        repository.file("zt001", "backend/local.var", "locals {}");
        repository.file("zt001", "backend/application.yaml.kv", "feature: false");

        var snapshots = new RuntimeConfigurationSourceLoader(repository).load(
                scope(),
                "dev1",
                "zt001"
        );

        assertTrue(snapshots.source().coverage().complete());
        assertTrue(snapshots.target().coverage().complete());
        assertEquals(
                "backend/application.yml.kv",
                snapshots.source().file(RuntimeConfigurationFileRole.APPLICATION_YAML).path()
        );
        assertEquals(
                "backend/application.yaml.kv",
                snapshots.target().file(RuntimeConfigurationFileRole.APPLICATION_YAML).path()
        );
        assertEquals(
                "commit-dev1",
                snapshots.source().coverage().files().get(0).commitId()
        );
    }

    @Test
    void shouldReportMissingAmbiguousTruncatedAndMissingBranchCoverage() {
        var repository = new FakeRepository();
        repository.branch("dev1").branch("zt001");
        repository.file("dev1", "global.var", "locals {}");
        repository.file("dev1", "backend/local.var", "locals {}");
        repository.file("dev1", "backend/application.yml.kv", "feature: true");
        repository.file("dev1", "backend/application.yaml.kv", "feature: true");
        repository.file("zt001", "global.var", "locals {}", true);
        repository.file("zt001", "backend/local.var", "locals {}");

        var snapshots = new RuntimeConfigurationSourceLoader(repository).load(
                scope(),
                "dev1",
                "zt001"
        );
        var missingBranch = new RuntimeConfigurationSourceLoader(repository).load(
                scope(),
                "dev9",
                "zt001"
        );

        assertFalse(snapshots.source().coverage().complete());
        assertEquals(
                RuntimeConfigurationFileStatus.AMBIGUOUS,
                coverage(snapshots.source(), RuntimeConfigurationFileRole.APPLICATION_YAML).status()
        );
        assertEquals(
                RuntimeConfigurationFileStatus.TRUNCATED,
                coverage(snapshots.target(), RuntimeConfigurationFileRole.GLOBAL_VAR).status()
        );
        assertEquals(
                RuntimeConfigurationFileStatus.MISSING,
                coverage(snapshots.target(), RuntimeConfigurationFileRole.APPLICATION_YAML).status()
        );
        assertFalse(missingBranch.source().coverage().branchExists());
        assertEquals(
                RuntimeConfigurationFileStatus.BRANCH_MISSING,
                missingBranch.source().coverage().files().get(0).status()
        );
        assertNull(snapshots.source().file(RuntimeConfigurationFileRole.APPLICATION_YAML));
    }

    private static RuntimeConfigurationFileCoverage coverage(
            RuntimeConfigurationRawSnapshot snapshot,
            RuntimeConfigurationFileRole role
    ) {
        return snapshot.coverage().files().stream()
                .filter(file -> file.role() == role)
                .findFirst()
                .orElseThrow();
    }

    private static RuntimeConfigurationScope scope() {
        return new RuntimeConfigurationScope(
                "runtime-config",
                "config-gitlab",
                "platform/runtime-config",
                "backend",
                "Backend",
                "backend"
        );
    }

    private static final class FakeRepository implements GitLabExactRepositoryPort {

        private final Set<String> branches = new LinkedHashSet<>();
        private final Map<String, StoredFile> files = new LinkedHashMap<>();

        private FakeRepository branch(String branch) {
            branches.add(branch);
            return this;
        }

        private FakeRepository file(String branch, String path, String content) {
            return file(branch, path, content, false);
        }

        private FakeRepository file(String branch, String path, String content, boolean truncated) {
            files.put(branch + ":" + path, new StoredFile(content, truncated));
            return this;
        }

        @Override
        public boolean branchExists(String connectionId, String projectPath, String branch) {
            return branches.contains(branch);
        }

        @Override
        public GitLabExactFileContent readFile(
                String connectionId,
                String projectPath,
                String ref,
                String filePath,
                int maxCharacters
        ) {
            var file = files.get(ref + ":" + filePath);
            if (file == null) {
                throw notFound();
            }
            return new GitLabExactFileContent(
                    connectionId,
                    projectPath,
                    ref,
                    filePath,
                    file.content,
                    file.content.length(),
                    file.truncated
            );
        }

        @Override
        public GitLabExactFileMetadata readFileMetadata(
                String connectionId,
                String projectPath,
                String ref,
                String filePath
        ) {
            var file = files.get(ref + ":" + filePath);
            if (file == null) {
                throw notFound();
            }
            return new GitLabExactFileMetadata(
                    connectionId,
                    projectPath,
                    ref,
                    filePath,
                    "blob-" + ref,
                    "commit-" + ref,
                    "last-" + ref,
                    "2026-07-30T00:00:00Z",
                    "sha-" + ref,
                    (long) file.content.length()
            );
        }

        private GitLabExactReadException notFound() {
            return GitLabExactReadException.upstream(
                    "file read",
                    "config-gitlab",
                    404,
                    null
            );
        }

        private record StoredFile(String content, boolean truncated) {
        }
    }

}
