package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryTreeException;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryTreeNode;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryTreeSession;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryTreeService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabEndpointUseCaseSourceSnapshotServiceTest {

    private static final String GROUP = "tenant-alpha";
    private static final String PROJECT = "orders-api";
    private static final String BRANCH = "main";

    @Test
    void shouldBuildSnapshotFromRepositoryTreeAndFilterJavaMainSources() {
        var fixture = fixture(10, 1_000);
        var treeSession = new GitLabRepositoryTreeSession();
        var request = request();

        when(fixture.treeService().requestScopedSession(anyString())).thenReturn(treeSession);
        when(fixture.treeService().fetchRepositoryBlobs(
                eq("https://gitlab.example"),
                eq(GROUP + "/" + PROJECT),
                eq(BRANCH),
                eq("src/main/java"),
                same(treeSession)
        )).thenReturn(List.of(
                node("src/main/java/com/example/orders/api/OrderController.java"),
                node("src/main/java/com/example/orders/service/OrderService.java"),
                node("src/main/java/com/example/orders/package-info.java"),
                node("src/test/java/com/example/orders/api/OrderControllerTest.java"),
                node("src/main/resources/application.yml"),
                node("target/generated-sources/com/example/Generated.java")
        ));
        whenRead(fixture.repositoryPort(), "src/main/java/com/example/orders/api/OrderController.java", "controller");
        whenRead(fixture.repositoryPort(), "src/main/java/com/example/orders/service/OrderService.java", "service");

        var snapshot = fixture.service().buildSnapshot(GROUP, BRANCH, request);

        assertEquals(GitLabEndpointUseCaseIndexStatus.BUILT_DURING_CALL, snapshot.indexStatus());
        assertEquals(6, snapshot.discoveredBlobCount());
        assertEquals(2, snapshot.eligibleSourceFileCount());
        assertEquals(List.of(
                "src/main/java/com/example/orders/api/OrderController.java",
                "src/main/java/com/example/orders/service/OrderService.java"
        ), snapshot.files().stream().map(GitLabEndpointUseCaseSourceFile::path).toList());
        assertEquals(List.of(GitLabEndpointUseCaseWarningCodes.BRANCH_REF_NOT_IMMUTABLE), warningCodes(snapshot));
        assertFalse(snapshot.sourceFileLimitReached());
        assertFalse(snapshot.readTruncationDetected());
    }

    @Test
    void shouldApplyFileLimitAndReportTruncatedContent() {
        var fixture = fixture(2, 10);
        var treeSession = new GitLabRepositoryTreeSession();

        when(fixture.treeService().requestScopedSession(anyString())).thenReturn(treeSession);
        when(fixture.treeService().fetchRepositoryBlobs(
                eq("https://gitlab.example"),
                eq(GROUP + "/" + PROJECT),
                eq(BRANCH),
                eq("src/main/java"),
                same(treeSession)
        )).thenReturn(List.of(
                node("src/main/java/com/example/orders/A.java"),
                node("src/main/java/com/example/orders/B.java"),
                node("src/main/java/com/example/orders/C.java")
        ));
        whenRead(fixture.repositoryPort(), "src/main/java/com/example/orders/A.java", "a");
        when(fixture.repositoryPort().readFile(
                eq(GROUP),
                eq(PROJECT),
                eq(BRANCH),
                eq("src/main/java/com/example/orders/B.java"),
                eq(10)
        )).thenReturn(new GitLabRepositoryFileContent(
                GROUP,
                PROJECT,
                BRANCH,
                "src/main/java/com/example/orders/B.java",
                "0123456789",
                true
        ));

        var snapshot = fixture.service().buildSnapshot(GROUP, BRANCH, request());

        assertEquals(GitLabEndpointUseCaseIndexStatus.PARTIAL, snapshot.indexStatus());
        assertEquals(3, snapshot.eligibleSourceFileCount());
        assertEquals(2, snapshot.files().size());
        assertTrue(snapshot.sourceFileLimitReached());
        assertTrue(snapshot.readTruncationDetected());
        assertTrue(warningCodes(snapshot).contains(GitLabEndpointUseCaseWarningCodes.SOURCE_FILE_LIMIT_REACHED));
        assertTrue(warningCodes(snapshot).contains(GitLabEndpointUseCaseWarningCodes.SOURCE_FILE_TRUNCATED));
        verify(fixture.repositoryPort(), never()).readFile(
                eq(GROUP),
                eq(PROJECT),
                eq(BRANCH),
                eq("src/main/java/com/example/orders/C.java"),
                eq(10)
        );
    }

    @Test
    void shouldReturnNotBuiltSnapshotWhenRepositoryTreeFails() {
        var fixture = fixture(10, 1_000);
        var treeSession = new GitLabRepositoryTreeSession();

        when(fixture.treeService().requestScopedSession(anyString())).thenReturn(treeSession);
        when(fixture.treeService().fetchRepositoryBlobs(
                eq("https://gitlab.example"),
                eq(GROUP + "/" + PROJECT),
                eq(BRANCH),
                eq("src/main/java"),
                same(treeSession)
        )).thenThrow(new GitLabRepositoryTreeException(404, "not found", null));

        var snapshot = fixture.service().buildSnapshot(GROUP, BRANCH, request());

        assertEquals(GitLabEndpointUseCaseIndexStatus.NOT_BUILT, snapshot.indexStatus());
        assertTrue(snapshot.files().isEmpty());
        assertTrue(warningCodes(snapshot).contains(GitLabEndpointUseCaseWarningCodes.BRANCH_REF_NOT_IMMUTABLE));
        assertTrue(warningCodes(snapshot).contains(GitLabEndpointUseCaseWarningCodes.SOURCE_TREE_UNAVAILABLE));
    }

    private static Fixture fixture(int maxSourceFiles, int maxFileCharacters) {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example");
        var treeService = mock(GitLabRepositoryTreeService.class);
        var repositoryPort = mock(GitLabRepositoryPort.class);
        var service = new GitLabEndpointUseCaseSourceSnapshotService(
                properties,
                treeService,
                repositoryPort,
                maxSourceFiles,
                maxFileCharacters
        );
        return new Fixture(service, treeService, repositoryPort);
    }

    private static GitLabEndpointUseCaseContextRequest request() {
        return new GitLabEndpointUseCaseContextRequest(
                PROJECT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "test"
        );
    }

    private static GitLabRepositoryTreeNode node(String path) {
        return new GitLabRepositoryTreeNode(path, "blob");
    }

    private static void whenRead(GitLabRepositoryPort repositoryPort, String filePath, String content) {
        when(repositoryPort.readFile(eq(GROUP), eq(PROJECT), eq(BRANCH), eq(filePath), eq(1_000)))
                .thenReturn(new GitLabRepositoryFileContent(GROUP, PROJECT, BRANCH, filePath, content, false));
        when(repositoryPort.readFile(eq(GROUP), eq(PROJECT), eq(BRANCH), eq(filePath), eq(10)))
                .thenReturn(new GitLabRepositoryFileContent(GROUP, PROJECT, BRANCH, filePath, content, false));
    }

    private static List<String> warningCodes(GitLabEndpointUseCaseSourceSnapshot snapshot) {
        return snapshot.warnings().stream()
                .map(GitLabEndpointUseCaseWarning::code)
                .toList();
    }

    private record Fixture(
            GitLabEndpointUseCaseSourceSnapshotService service,
            GitLabRepositoryTreeService treeService,
            GitLabRepositoryPort repositoryPort
    ) {
    }
}
