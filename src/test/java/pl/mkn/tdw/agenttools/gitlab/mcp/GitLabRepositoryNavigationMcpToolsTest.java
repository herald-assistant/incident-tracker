package pl.mkn.tdw.agenttools.gitlab.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryBranchService;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFilePage;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevision;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeExplorer;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreeNode;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryTreePage;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.testsupport.agenttools.GitLabMcpToolsTestCreator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabRepositoryNavigationMcpToolsTest {

    private static final String SELECTED_COMMIT = "1234567890abcdef1234567890abcdef12345678";
    private static final String OTHER_COMMIT = "abcdef1234567890abcdef1234567890abcdef12";

    private final GitLabRepositoryPort port = mock(GitLabRepositoryPort.class);
    private final GitLabRepositoryBranchService branchService = mock(GitLabRepositoryBranchService.class);
    private final GitLabRepositoryToolScope scope = new GitLabRepositoryToolScope(
            "CRM", "PROCESSES/customer-profile", "main", SELECTED_COMMIT);
    private final GitLabRepositoryNavigationMcpTools tools = new GitLabRepositoryNavigationMcpTools(
            port, new GitLabRepositoryTreeExplorer(port), branchService, new GitLabProperties(),
            mock(OperationalContextPort.class));

    @Test
    void listsBranchesOfAnUncataloguedProjectInsideTheConfiguredMainGroup() {
        when(branchService.listBranches("CRM/LIBS/shared", "release"))
                .thenReturn(new GitLabRepositoryBranchService.BranchPage(List.of(
                        new GitLabRepositoryBranchService.Branch("release/1", true)), false));

        var result = tools.listRepositoryBranches("LIBS/shared", "release", "Ustalam gałąź biblioteki.", context());

        assertThat(result.projectPath()).isEqualTo("CRM/LIBS/shared");
        assertThat(result.branches()).extracting(GitLabRepositoryBranchService.Branch::name)
                .containsExactly("release/1");
        assertThat(result.branches().get(0).isDefault()).isTrue();
        assertThat(scope.readSourceRefs()).isEmpty();
        assertThatThrownBy(() -> tools.listRepositoryBranches("https://other.invalid/project", "", "Powód.", context()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectedProjectUsesPinnedCommitAndCanNavigateSubgroupWithoutResolvingAgain() {
        when(port.listRepositoryTreeChildrenPage("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT,
                "", "", 40)).thenReturn(new GitLabRepositoryTreePage(
                List.of(new GitLabRepositoryTreeNode("pom.xml", "blob")), null));
        when(port.listRepositoryFilesPage("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT,
                "", "", 200)).thenReturn(new GitLabRepositoryFilePage(List.of(
                new GitLabRepositoryFile("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT, "pom.xml"),
                new GitLabRepositoryFile("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT, "secrets.xml")
        ), null));

        var tree = tools.listRepositoryTree("CRM/PROCESSES/customer-profile", "main", "", "",
                "Sprawdzam moduły.", context());
        var files = tools.listRepositoryFiles("PROCESSES/customer-profile", "main", "", "",
                "Szukam plików projektu.", context());

        assertThat(tree.projectPath()).isEqualTo("CRM/PROCESSES/customer-profile");
        assertThat(tree.commitId()).isEqualTo(SELECTED_COMMIT);
        assertThat(tree.entries()).extracting(entry -> entry.path()).containsExactly("pom.xml");
        assertThat(files.paths()).containsExactly("pom.xml", "secrets.xml");
        verify(port, never()).resolveRevision("CRM", "PROCESSES/customer-profile", "main");
        assertThat(scope.readSourceRefs()).isEmpty();
    }

    @Test
    void anotherProjectInMainGroupIsPinnedAndSearchOnlyReturnsMatchingPaths() {
        when(port.resolveRevision("CRM", "LIBS/shared", "release/1"))
                .thenReturn(new GitLabRepositoryRevision("CRM", "LIBS/shared", "release/1", OTHER_COMMIT, null));
        when(port.searchRepositoryFilesByContent("CRM", "LIBS/shared", "release/1",
                List.of("customer-profile-client"), 20)).thenReturn(List.of(
                new GitLabRepositoryFileCandidate("CRM", "LIBS/shared", "release/1",
                        "pom.xml", "match", 1),
                new GitLabRepositoryFileCandidate("CRM", "LIBS/shared", "release/1",
                        "credentials.json", "match", 1)
        ));
        var content = "<project><artifactId>customer-profile-client</artifactId></project>";
        when(port.readFileMetadata("CRM", "LIBS/shared", OTHER_COMMIT, "pom.xml"))
                .thenReturn(new GitLabRepositoryFileMetadata("CRM", "LIBS/shared", OTHER_COMMIT,
                        "pom.xml", null, OTHER_COMMIT, null, null, null, (long) content.length()));
        when(port.readFileBounded("CRM", "LIBS/shared", OTHER_COMMIT, "pom.xml", 256 * 1024))
                .thenReturn(new GitLabRepositoryFileContent("CRM", "LIBS/shared", OTHER_COMMIT,
                        "pom.xml", content, false));
        var configuration = "customer-profile-client=fictional-example";
        when(port.readFileMetadata("CRM", "LIBS/shared", OTHER_COMMIT, "credentials.json"))
                .thenReturn(new GitLabRepositoryFileMetadata("CRM", "LIBS/shared", OTHER_COMMIT,
                        "credentials.json", null, OTHER_COMMIT, null, null, null, (long) configuration.length()));
        when(port.readFileBounded("CRM", "LIBS/shared", OTHER_COMMIT, "credentials.json", 256 * 1024))
                .thenReturn(new GitLabRepositoryFileContent("CRM", "LIBS/shared", OTHER_COMMIT,
                        "credentials.json", configuration, false));

        var result = tools.searchRepositoryFiles("LIBS/shared", "release/1", "customer-profile-client", "", "",
                "Sprawdzam zależność biblioteki.", context());
        assertThat(result.projectPath()).isEqualTo("CRM/LIBS/shared");
        assertThat(result.commitId()).isEqualTo(OTHER_COMMIT);
        assertThat(result.paths()).containsExactly("pom.xml", "credentials.json");
        assertThat(scope.readSourceRefs()).isEmpty();
        scope.resolve("LIBS/shared", "release/1", port);
        verify(port).resolveRevision("CRM", "LIBS/shared", "release/1");
    }

    @Test
    void searchesPinnedCommitByBoundedScanWhenBlobSearchFindsNoCandidate() {
        var content = "<project><artifactId>customer-profile-client</artifactId></project>";
        when(port.searchRepositoryFilesByContent("CRM", "PROCESSES/customer-profile", "main",
                List.of("customer-profile-client"), 20)).thenReturn(List.of());
        when(port.listRepositoryFilesPage("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT,
                "", "", 20)).thenReturn(new GitLabRepositoryFilePage(List.of(
                new GitLabRepositoryFile("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT, "pom.xml")
        ), "nextCursor"));
        when(port.readFileMetadata("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT, "pom.xml"))
                .thenReturn(new GitLabRepositoryFileMetadata("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT,
                        "pom.xml", null, SELECTED_COMMIT, null, null, null, (long) content.length()));
        when(port.readFileBounded("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT, "pom.xml", 256 * 1024))
                .thenReturn(new GitLabRepositoryFileContent("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT,
                        "pom.xml", content, false));

        var result = tools.searchRepositoryFiles("PROCESSES/customer-profile", "main", "customer-profile-client",
                "", "", "Szukam zależności w POM.", context());

        assertThat(result.paths()).containsExactly("pom.xml");
        assertThat(result.nextCursor()).isEqualTo("nextCursor");
        assertThat(result.truncated()).isTrue();
        assertThat(scope.readSourceRefs()).isEmpty();
    }

    @Test
    void verifiedReadRegistersExactSourceRefAndCannotChangeSelectedBranch() {
        var content = "<project><artifactId>customer-profile-client</artifactId></project>";
        when(port.readFileMetadata("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT, "pom.xml"))
                .thenReturn(new GitLabRepositoryFileMetadata("CRM", "PROCESSES/customer-profile",
                        SELECTED_COMMIT, "pom.xml", null, SELECTED_COMMIT, null, null,
                        null, (long) content.length()));
        when(port.readFileBounded("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT,
                "pom.xml", 256 * 1024)).thenReturn(new GitLabRepositoryFileContent(
                "CRM", "PROCESSES/customer-profile", SELECTED_COMMIT, "pom.xml", content, false));
        var gitlab = GitLabMcpToolsTestCreator.create(port);

        var result = gitlab.readRepositoryFile("PROCESSES/customer-profile", "main", List.of(),
                "pom.xml", null, "Sprawdzam zależności w POM.", context());
        assertThat(result.sourceRef()).isEqualTo(
                "gitlab:CRM/PROCESSES/customer-profile@" + SELECTED_COMMIT + ":pom.xml");
        assertThat(scope.readSourceRefs()).containsExactly(result.sourceRef());

        var configuration = "apiToken=fictional-example";
        when(port.readFileMetadata("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT, ".env"))
                .thenReturn(new GitLabRepositoryFileMetadata("CRM", "PROCESSES/customer-profile",
                        SELECTED_COMMIT, ".env", null, SELECTED_COMMIT, null, null,
                        null, (long) configuration.length()));
        when(port.readFileBounded("CRM", "PROCESSES/customer-profile", SELECTED_COMMIT,
                ".env", 256 * 1024)).thenReturn(new GitLabRepositoryFileContent(
                "CRM", "PROCESSES/customer-profile", SELECTED_COMMIT, ".env", configuration, false));
        var raw = gitlab.readRepositoryFile("PROCESSES/customer-profile", "main", List.of(),
                ".env", null, "Sprawdzam konfigurację CRM.", context());
        assertThat(raw.content()).isEqualTo(configuration);
        assertThat(raw.sourceRef()).endsWith(":.env");

        assertThatThrownBy(() -> gitlab.readRepositoryFile("PROCESSES/customer-profile", "other", List.of(),
                "pom.xml", null, "Zmieniam gałąź.", context()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gitlab.readRepositoryFile("../outside", "main", List.of(),
                "pom.xml", null, "Próbuję wyjść poza grupę.", context()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(scope.readSourceRefs()).containsExactlyInAnyOrder(result.sourceRef(), raw.sourceRef());
    }

    private ToolContext context() {
        return new ToolContext(Map.of(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE, scope));
    }
}
