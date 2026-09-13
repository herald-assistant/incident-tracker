package pl.mkn.tdw.features.operationalcontextassistance.source;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevision;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeExplorer;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeSlice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationalContextGitLabSourceCollectorTest {

    private static final String GROUP = "CRM/runtime";
    private static final String PROJECT = "customer-api";
    private static final String REF = "main";
    private static final String COMMIT = "1234567890abcdef1234567890abcdef12345678";
    private static final String README = "# Customer API\nHandles customer lookups.\n";

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabRepositoryTreeExplorer treeExplorer = mock(GitLabRepositoryTreeExplorer.class);
    private final OperationalContextGitLabSourceCollector collector =
            new OperationalContextGitLabSourceCollector(properties(), repositoryPort, treeExplorer);

    @Test
    void shouldPinRequestedRefAndReadOnlyExactAllowedPathWithVerifiedHash() {
        when(repositoryPort.resolveRevision(GROUP, PROJECT, REF)).thenReturn(revision());
        when(repositoryPort.readFileMetadata(GROUP, PROJECT, COMMIT, "README.md"))
                .thenReturn(metadata("README.md", README));
        when(repositoryPort.readFileBounded(GROUP, PROJECT, COMMIT, "README.md", 16 * 1024))
                .thenReturn(content("README.md", README));

        var snapshot = collector.collect(PROJECT, REF);

        assertEquals(COMMIT, snapshot.commitId());
        assertEquals(1, snapshot.files().size());
        assertEquals(README, snapshot.files().get(0).content());
        assertEquals("gitlab:" + GROUP + "/" + PROJECT + "@" + COMMIT + ":README.md",
                snapshot.files().get(0).sourceRef());
        verify(repositoryPort, never()).listRepositoryFiles(GROUP, PROJECT, COMMIT, null);
    }

    @Test
    void capturesNavigationTreeEvenWhenNoRootAllowlistFileCanBeRead() {
        when(repositoryPort.resolveRevision(GROUP, PROJECT, REF)).thenReturn(revision());
        var tree = new GitLabRepositoryTreeSlice("", 4, List.of(
                new GitLabRepositoryTreeSlice.Entry("Backend", "tree"),
                new GitLabRepositoryTreeSlice.Entry("Backend/hackhub-backend", "tree"),
                new GitLabRepositoryTreeSlice.Entry("Frontend", "tree")
        ), List.of(), false);
        when(treeExplorer.explore(GROUP, PROJECT, COMMIT, "", "")).thenReturn(tree);

        var snapshot = collector.collect(PROJECT, REF);

        assertTrue(snapshot.files().isEmpty());
        assertEquals(tree, snapshot.tree());
        assertEquals(COMMIT, snapshot.commitId());
        verify(treeExplorer).explore(GROUP, PROJECT, COMMIT, "", "");
    }

    @Test
    void shouldStripOnlyTheConfiguredGroupFromManuallyEnteredFullProjectPath() {
        var properties = new GitLabProperties();
        properties.setGroup("unicam-group");
        var collector = new OperationalContextGitLabSourceCollector(properties, repositoryPort, treeExplorer);

        var snapshot = collector.collect("unicam-group/Unicam-project", REF);

        assertEquals("Unicam-project", snapshot.project());
        verify(repositoryPort).resolveRevision("unicam-group", "Unicam-project", REF);
        verify(repositoryPort, never()).resolveRevision("unicam-group", "unicam-group/Unicam-project", REF);
    }

    @Test
    void shouldUseNormalizedConfiguredGroupForRevisionAndFullPathSelection() {
        var properties = new GitLabProperties();
        properties.setGroup(" /CRM/runtime/ ");
        var collector = new OperationalContextGitLabSourceCollector(properties, repositoryPort, treeExplorer);
        when(repositoryPort.resolveRevision(GROUP, PROJECT, REF)).thenReturn(revision());

        var snapshot = collector.collect("crm/RUNTIME/" + PROJECT, REF);

        assertEquals(PROJECT, snapshot.project());
        assertEquals(COMMIT, snapshot.commitId());
        verify(repositoryPort).resolveRevision(GROUP, PROJECT, REF);
        verify(repositoryPort, never()).resolveRevision(" /CRM/runtime/ ", PROJECT, REF);
    }

    @Test
    void shouldKeepOtherSafePathAsSubgroupWithinConfiguredScope() {
        var relativeSubgroup = "other-group/customer-api";

        collector.collect(relativeSubgroup, REF);

        verify(repositoryPort).resolveRevision(GROUP, relativeSubgroup, REF);
    }

    @Test
    void shouldDeriveNestedRepositoryIdentityFromFullProjectUrl() {
        var properties = urlProperties("CLP", "https://gitlab.example.com");
        var collector = new OperationalContextGitLabSourceCollector(properties, repositoryPort, treeExplorer);

        var snapshot = collector.collectUrl(
                "https://gitlab.example.com/CLP/PROCESSES/CLP_AGREEMENT_PROCESS", REF);

        assertEquals("PROCESSES/CLP_AGREEMENT_PROCESS", snapshot.project());
        assertEquals("gitlab", snapshot.repositoryGit().provider());
        assertEquals("CLP/PROCESSES", snapshot.repositoryGit().group());
        assertEquals("CLP_AGREEMENT_PROCESS", snapshot.repositoryGit().project());
        assertEquals("CLP/PROCESSES/CLP_AGREEMENT_PROCESS", snapshot.repositoryGit().projectPath());
        assertEquals("https://gitlab.example.com/CLP/PROCESSES/CLP_AGREEMENT_PROCESS",
                snapshot.repositoryGit().url());
        verify(repositoryPort).resolveRevision("CLP", "PROCESSES/CLP_AGREEMENT_PROCESS", REF);
    }

    @Test
    void resolvesBranchLookupToTheSameNestedProjectAsTheAnalysis() {
        var collector = new OperationalContextGitLabSourceCollector(
                urlProperties("CLP", "https://gitlab.example.com"), repositoryPort, treeExplorer);

        assertEquals("CLP/PROCESSES/CLP_AGREEMENT_PROCESS",
                collector.projectPathForBranchOptions("PROCESSES/CLP_AGREEMENT_PROCESS", null));
        assertEquals("CLP/PROCESSES/CLP_AGREEMENT_PROCESS",
                collector.projectPathForBranchOptions(null,
                        "https://gitlab.example.com/CLP/PROCESSES/CLP_AGREEMENT_PROCESS"));
        assertThrows(OperationalContextGitLabSourceSelectionException.class,
                () -> collector.projectPathForBranchOptions(null, "https://gitlab.example.com/OTHER/project"));
        assertThrows(OperationalContextGitLabSourceSelectionException.class,
                () -> collector.projectPathForBranchOptions("PROCESSES/project", "https://gitlab.example.com/CLP/project"));
        verifyNoInteractions(repositoryPort);
    }

    @Test
    void shouldAcceptCloneUrlSuffixUnderConfiguredBasePath() {
        var properties = urlProperties("CLP", "https://gitlab.example.com/gitlab/");
        var collector = new OperationalContextGitLabSourceCollector(properties, repositoryPort, treeExplorer);

        var snapshot = collector.collectUrl(
                "https://gitlab.example.com/gitlab/CLP/PROCESSES/CLP_AGREEMENT_PROCESS.git/", REF);

        assertEquals("PROCESSES/CLP_AGREEMENT_PROCESS", snapshot.project());
        assertEquals("https://gitlab.example.com/gitlab/CLP/PROCESSES/CLP_AGREEMENT_PROCESS",
                snapshot.repositoryGit().url());
        verify(repositoryPort).resolveRevision("CLP", "PROCESSES/CLP_AGREEMENT_PROCESS", REF);
    }

    @Test
    void shouldRejectOtherHostPortBasePathOrRootGroupBeforeAnyGitLabCall() {
        var collector = new OperationalContextGitLabSourceCollector(
                urlProperties("CLP", "https://gitlab.example.com/gitlab"), repositoryPort, treeExplorer);

        for (var url : new String[]{
                "https://gitlab.example.com.evil/gitlab/CLP/PROCESSES/project",
                "https://gitlab.example.com:8443/gitlab/CLP/PROCESSES/project",
                "http://gitlab.example.com/gitlab/CLP/PROCESSES/project",
                "https://gitlab.example.com/gitlab-extra/CLP/PROCESSES/project",
                "https://gitlab.example.com/gitlab/CLPX/PROCESSES/project",
                "https://gitlab.example.com/gitlab/OTHER/project"
        }) {
            assertThrows(OperationalContextGitLabSourceSelectionException.class,
                    () -> collector.validateProjectUrl(url), url);
        }
        verifyNoInteractions(repositoryPort);
    }

    @Test
    void shouldRejectAmbiguousOrNonProjectUrl() {
        var collector = new OperationalContextGitLabSourceCollector(
                urlProperties("CLP", "https://gitlab.example.com"), repositoryPort, treeExplorer);

        for (var url : new String[]{
                "https://user@gitlab.example.com/CLP/project",
                "https://gitlab.example.com/CLP/project?ref=main",
                "https://gitlab.example.com/CLP/project#readme",
                "https://gitlab.example.com/CLP/project/-/tree/main",
                "https://gitlab.example.com/CLP/%2e%2e/project",
                "https://gitlab.example.com/CLP%2FOTHER/project",
                "https://gitlab.example.com/CLP//project",
                "https://gitlab.example.com/CLP/../OTHER/project",
                "https://gitlab.example.com/CLP/"
        }) {
            assertThrows(OperationalContextGitLabSourceSelectionException.class,
                    () -> collector.validateProjectUrl(url), url);
        }
        verifyNoInteractions(repositoryPort);
    }

    @Test
    void shouldSkipOversizeFileBeforeAnyBodyRead() {
        when(repositoryPort.resolveRevision(GROUP, PROJECT, REF)).thenReturn(revision());
        when(repositoryPort.readFileMetadata(GROUP, PROJECT, COMMIT, "README.md"))
                .thenReturn(new GitLabRepositoryFileMetadata(
                        GROUP, PROJECT, COMMIT, "README.md", null, COMMIT, null, null, null, 16_385L
                ));

        var snapshot = collector.collect(PROJECT, REF);

        assertTrue(snapshot.files().isEmpty());
        assertTrue(snapshot.visibilityLimits().stream().anyMatch(limit -> limit.contains("README.md")
                && limit.contains("limit")));
        verify(repositoryPort, never()).readFileBounded(GROUP, PROJECT, COMMIT, "README.md", 16 * 1024);
    }

    @Test
    void shouldOmitWholeFileWhenItContainsPossibleSecret() {
        var sensitive = "# Setup\npassword=real-secret-value\n";
        when(repositoryPort.resolveRevision(GROUP, PROJECT, REF)).thenReturn(revision());
        when(repositoryPort.readFileMetadata(GROUP, PROJECT, COMMIT, "README.md"))
                .thenReturn(metadata("README.md", sensitive));
        when(repositoryPort.readFileBounded(GROUP, PROJECT, COMMIT, "README.md", 16 * 1024))
                .thenReturn(content("README.md", sensitive));

        var snapshot = collector.collect(PROJECT, REF);

        assertTrue(snapshot.files().isEmpty());
        assertTrue(snapshot.visibilityLimits().stream().anyMatch(limit -> limit.contains("wrażliwą")));
        assertFalse(snapshot.toString().contains("real-secret-value"));
    }

    @Test
    void shouldRejectProjectThatCouldEscapeConfiguredGroup() {
        assertThrows(IllegalArgumentException.class, () -> collector.collect("../other/project", REF));
        verify(repositoryPort, never()).resolveRevision(GROUP, "../other/project", REF);
    }

    @Test
    void shouldReturnVisibilityLimitWhenRevisionCannotBePinned() {
        var snapshot = collector.collect(PROJECT, REF);

        assertTrue(snapshot.files().isEmpty());
        assertEquals(null, snapshot.commitId());
        assertTrue(snapshot.visibilityLimits().stream().anyMatch(limit -> limit.contains("commita")));
    }

    private GitLabProperties properties() {
        var value = new GitLabProperties();
        value.setGroup(GROUP);
        return value;
    }

    private GitLabProperties urlProperties(String group, String baseUrl) {
        var value = new GitLabProperties();
        value.setGroup(group);
        value.setBaseUrl(baseUrl);
        return value;
    }

    private GitLabRepositoryRevision revision() {
        return new GitLabRepositoryRevision(GROUP, PROJECT, REF, COMMIT, null);
    }

    private GitLabRepositoryFileMetadata metadata(String path, String content) {
        return new GitLabRepositoryFileMetadata(
                GROUP, PROJECT, COMMIT, path, null, COMMIT, null, null,
                sha256(content), (long) content.getBytes(StandardCharsets.UTF_8).length
        );
    }

    private GitLabRepositoryFileContent content(String path, String content) {
        return new GitLabRepositoryFileContent(GROUP, PROJECT, COMMIT, path, content, false);
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
