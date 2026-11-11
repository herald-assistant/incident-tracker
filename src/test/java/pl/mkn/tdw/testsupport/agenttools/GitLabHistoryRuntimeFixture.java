package pl.mkn.tdw.testsupport.agenttools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import com.github.copilot.rpc.ToolInvocation;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabHistoryMcpTools;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotToolInvocationHandler;
import pl.mkn.tdw.aiplatform.copilot.tools.context.*;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.tdw.integrations.gitlab.*;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.contract.*;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextNavigationTestCreator;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures;

import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes real Spring callbacks through the Copilot bridge using a fictional CRM repository. */
public final class GitLabHistoryRuntimeFixture {
    public static final String SHA = "d".repeat(40), PARENT = "e".repeat(40);
    public final CopilotSdkToolFactory factory;
    public final List<AnalysisEvidenceSection> evidence = new ArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CopilotToolSessionContext session;
    private final String project, path;

    public GitLabHistoryRuntimeFixture(CopilotToolSessionContext session, String project, String path,
                                       CopilotToolInvocationPolicy... policies) {
        this.session = session; this.project = project; this.path = path;
        var group = (String) session.hiddenContext().getOrDefault(AgentToolContextKeys.GITLAB_GROUP, "CRM");
        var settings = new GitLabProperties(); settings.setGroup(group);
        var repository = mock(GitLabRepositoryPort.class);
        when(repository.resolveRevision(eq(group), eq(project), anyString())).thenAnswer(call ->
                new GitLabRepositoryRevision(group, project, call.getArgument(2), "c".repeat(40), null));
        when(repository.readFileMetadata(group, project, SHA, path)).thenReturn(new GitLabRepositoryFileMetadata(
                group, project, SHA, path, null, SHA, SHA, null, null, 3L));
        when(repository.readFileBounded(eq(group), eq(project), eq(SHA), eq(path), anyInt()))
                .thenReturn(new GitLabRepositoryFileContent(group, project, SHA, path, "new", false));
        when(repository.readFile(eq(group), eq(project), eq(SHA), eq(path), anyInt()))
                .thenReturn(new GitLabRepositoryFileContent(group, project, SHA, path, "new", false));
        when(repository.readFileComplete(group, project, SHA, path))
                .thenReturn(new GitLabRepositoryFileContent(group, project, SHA, path, "new", false));
        when(repository.readFileChunk(eq(group), eq(project), eq(PARENT), eq(path), eq(1), eq(1), anyInt()))
                .thenReturn(new GitLabRepositoryFileChunk(group, project, PARENT, path, 1, 1, 1, 1, 1, "old", false));
        var history = mock(GitLabCommitHistoryPort.class);
        when(history.listCommits(eq(group), eq(project), anyString(), eq(path), isNull(), isNull(), eq(1)))
                .thenReturn(new GitLabCommitPage(List.of(new GitLabHistoryCommit(SHA, List.of(PARENT), Instant.EPOCH,
                        "Walidacja telefonu", "https://gitlab.example.com/CRM/customer/-/commit/" + SHA,
                        "CRM Integrator", "integrator@example.com")), null));
        when(history.readDiff(group, project, SHA, 1)).thenReturn(new GitLabCommitDiffPage(List.of(
                new GitLabMergeRequestDiffSnapshot.FileDiff(path, path, false, false, false, "-old\n+new", false, false)), null));
        var applications = (List<?>) session.hiddenContext().getOrDefault(AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES, List.of());
        var system = applications.isEmpty() ? "crm-contacts" : (String) applications.get(0);
        var catalog = OperationalContextDtos.catalogFromRaw(List.of(), List.of(), List.of(Map.of("id", system)), List.of(),
                List.of(Map.of("id", "crm-repo", "git", Map.of("group", group, "project", project, "projectPath", group + "/" + project))),
                List.of(Map.of("id", "crm-code", "target", Map.of("type", "system", "id", system), "repositories",
                        List.of(Map.of("repoId", "crm-repo", "role", "primary", "searchMode", "whole-repository")))),
                List.of(), List.of(), List.of(), List.of());
        var historyTools = new GitLabHistoryMcpTools(history, repository, settings, ignored -> catalog,
                OperationalContextNavigationTestCreator.navigation());
        var reads = GitLabMcpToolsTestCreator.create(repository, ignored -> catalog, settings);
        var provider = MethodToolCallbackProvider.builder().toolObjects(historyTools, reads).build();
        var store = new CopilotToolEvidenceSessionStore(); store.registerSession(session.copilotSessionId(), evidence::add);
        factory = new CopilotSdkToolFactory(List.of(provider), mapper, List.of(), new CopilotToolInvocationHandler(
                mapper, new CopilotToolContextFactory(), List.of(policies), CopilotTestFixtures.toolInvocationEventPublisher(mapper, store)));
    }

    public List<ToolDefinition> definitions() { return factory.createToolDefinitions(session, CopilotToolDescriptionContext.empty()); }

    public void exercise(List<ToolDefinition> enabled) {
        assertThat(call(enabled, GitLabToolNames.READ_COMMIT_DIFF,
                Map.of("projectName", project, "commitId", SHA, "reason", "Sprawdzam zmianę."))).contains("-old", "+new");
        assertThat(call(enabled, GitLabToolNames.READ_REPOSITORY_FILE,
                Map.of("projectName", project, "branchRef", SHA, "filePath", path, "reason", "Czytam kod."))).contains("\"content\":\"new\"");
        assertThat(call(enabled, GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                Map.of("projectName", project, "branchRef", PARENT, "filePath", path, "startLine", 1, "endLine", 1,
                        "reason", "Czytam wcześniejszy kod."))).contains("\"content\":\"old\"");
        assertThat(call(enabled, GitLabToolNames.READ_REPOSITORY_FILE,
                Map.of("projectName", project, "branchRef", "f".repeat(40), "filePath", path, "reason", "Sprawdzam.")))
                .doesNotContain("\"content\"");
        assertThat(call(enabled, GitLabToolNames.LIST_REPOSITORY_COMMITS,
                Map.of("projectName", project, "filePaths", List.of(path), "reason", "Sprawdzam historię.")))
                .contains("CRM Integrator", "integrator@example.com", SHA);
        assertThat(evidence.toString()).contains("CRM Integrator", "integrator@example.com", "gitlab_read_commit_diff");
    }

    public void verifyRepositoryReadBoundaries(List<ToolDefinition> enabled) {
        for (var args : List.of(
                Map.of("projectName", "other-crm-project", "branchRef", "main", "filePath", path, "reason", "Sprawdzam zakres."),
                Map.of("projectName", project, "branchRef", "main", "filePath", "../secret.txt", "reason", "Sprawdzam zakres."),
                Map.of("projectName", project, "branchRef", "main", "filePath", path, "reason", ""))) {
            assertThat(call(enabled, GitLabToolNames.READ_REPOSITORY_FILE, new HashMap<>(args)))
                    .contains("tool_error").doesNotContain("\"content\"");
        }
    }

    private String call(List<ToolDefinition> enabled, String name, Map<String, Object> args) {
        var tool = enabled.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
        var result = tool.handler().invoke(new ToolInvocation().setSessionId(session.copilotSessionId())
                .setToolCallId(UUID.randomUUID().toString()).setToolName(name).setArguments(mapper.valueToTree(args))).join();
        return mapper.valueToTree(result).toString();
    }
}
