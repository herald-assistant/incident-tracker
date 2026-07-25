package pl.mkn.tdw.features.changeverification.ai.copilot;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.database.DatabaseToolNames;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotNamedSkillDirectoryResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.changeverification.ai.preparation.ChangeVerificationPromptPreparation;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobMode;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationChangedFileSnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationRepositorySnapshot;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.integrations.gitlab.instructions.InstructionSource;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeVerificationCopilotRunRequestAssemblerTest {

    private static final CopilotToolDescriptionContext CHANGE_VERIFICATION_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("change-verification");

    @TempDir
    Path tempDirectory;

    @Test
    void shouldAssembleRunRequestWithRepositoryScopeAndOnlyChangeVerificationTools() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var contextCaptor = ArgumentCaptor.forClass(CopilotToolSessionContext.class);
        var assembler = new ChangeVerificationCopilotRunRequestAssembler(
                toolFactory,
                new ChangeVerificationCopilotToolSessionContextFactory(),
                skillDirectoryResolver(),
                new CopilotRunAuthMapper()
        );
        var gitLabReadTool = tool(GitLabToolNames.READ_REPOSITORY_FILE);
        var gitLabSearchTool = tool(GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES);
        var databaseTool = tool(DatabaseToolNames.DESCRIBE_TABLE);
        var rawSqlTool = tool(DatabaseToolNames.EXECUTE_READONLY_SQL);

        when(toolFactory.createToolDefinitions(
                contextCaptor.capture(),
                eq(CHANGE_VERIFICATION_DESCRIPTION_CONTEXT)
        )).thenReturn(List.of(databaseTool, rawSqlTool, gitLabReadTool, gitLabSearchTool));

        var runRequest = assembler.assemble(
                "cv-123",
                request(),
                sourceDiscovery(),
                preparation(),
                AnalysisAiAuthRef.localToken(null)
        );
        var sessionConfig = runRequest.sessionConfigRequest();
        var toolContext = contextCaptor.getValue();

        assertEquals("cv-123", runRequest.runReference());
        assertEquals("change-verification-cv-123", sessionConfig.sessionId());
        assertEquals("Change Verification prompt", runRequest.prompt());
        assertEquals(preparation().artifactContents(), runRequest.artifactContents());
        assertEquals(List.of(databaseTool, gitLabReadTool, gitLabSearchTool), sessionConfig.tools());
        assertEquals(
                List.of(
                        DatabaseToolNames.DESCRIBE_TABLE,
                        GitLabToolNames.READ_REPOSITORY_FILE,
                        GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES
                ),
                sessionConfig.availableToolNames()
        );
        assertThat(sessionConfig.effectiveAvailableToolNames()).contains("skill");
        assertSkillDirectories(sessionConfig.skillDirectories(), ChangeVerificationCopilotRuntimeSkillNames.initialSkillNames());

        var hiddenContext = toolContext.hiddenContext();
        assertEquals("cv-123", hiddenContext.get(AgentToolContextKeys.ANALYSIS_RUN_ID));
        assertEquals("change-verification-cv-123", hiddenContext.get(AgentToolContextKeys.COPILOT_SESSION_ID));
        assertEquals(
                ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE,
                hiddenContext.get(ChangeVerificationCopilotToolContextKeys.FEATURE)
        );
        assertEquals(
                ChangeVerificationCopilotToolContextKeys.RUN_KIND_COMPLIANCE,
                hiddenContext.get(ChangeVerificationCopilotToolContextKeys.RUN_KIND)
        );
        assertEquals(true, hiddenContext.get(ChangeVerificationCopilotToolContextKeys.DATABASE_READONLY_ONLY));
        assertEquals("sandbox-a", hiddenContext.get(AgentToolContextKeys.ENVIRONMENT));
        assertEquals(
                "customer-api",
                hiddenContext.get(ChangeVerificationCopilotToolContextKeys.DATABASE_APPLICATION)
        );
        assertEquals(true, hiddenContext.get(ChangeVerificationCopilotToolContextKeys.REPOSITORY_SCOPE_RESOLVED));
        assertThat(hiddenContext).doesNotContainKeys(AgentToolContextKeys.GITLAB_GROUP, AgentToolContextKeys.GITLAB_BRANCH);

        assertThat((List<?>) hiddenContext.get(ChangeVerificationCopilotToolContextKeys.ALLOWED_REPOSITORIES))
                .singleElement()
                .satisfies(repository -> assertThat(repositoryScope(repository))
                        .containsEntry("projectPath", "CRM/runtime/customer-api")
                        .containsEntry("projectName", "customer-api")
                        .containsEntry("sourceRef", "feature/CRM-123-status"));

        verify(toolFactory).createToolDefinitions(toolContext, CHANGE_VERIFICATION_DESCRIPTION_CONTEXT);
    }

    @Test
    void shouldAssembleSmokePackRunWithSmokePackSkillAndRunKind() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var contextCaptor = ArgumentCaptor.forClass(CopilotToolSessionContext.class);
        var assembler = new ChangeVerificationCopilotRunRequestAssembler(
                toolFactory,
                new ChangeVerificationCopilotToolSessionContextFactory(),
                skillDirectoryResolver(),
                new CopilotRunAuthMapper()
        );

        when(toolFactory.createToolDefinitions(
                contextCaptor.capture(),
                eq(CHANGE_VERIFICATION_DESCRIPTION_CONTEXT)
        )).thenReturn(List.of(tool(GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE)));

        var runRequest = assembler.assemble(
                "cv-123-smoke",
                request(),
                sourceDiscovery(),
                preparation(),
                AnalysisAiAuthRef.localToken(null),
                ChangeVerificationCopilotRuntimeSkillNames.smokePackSkillNames(),
                ChangeVerificationCopilotToolContextKeys.RUN_KIND_SMOKE_PACK
        );

        assertSkillDirectories(runRequest.sessionConfigRequest().skillDirectories(),
                ChangeVerificationCopilotRuntimeSkillNames.smokePackSkillNames());
        assertEquals(
                ChangeVerificationCopilotToolContextKeys.RUN_KIND_SMOKE_PACK,
                contextCaptor.getValue().hiddenContext().get(ChangeVerificationCopilotToolContextKeys.RUN_KIND)
        );
    }

    private CopilotNamedSkillDirectoryResolver skillDirectoryResolver() {
        var properties = new CopilotSdkProperties();
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        return new CopilotNamedSkillDirectoryResolver(new CopilotSkillRuntimeLoader(properties));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> repositoryScope(Object value) {
        return (Map<String, Object>) value;
    }

    private static void assertSkillDirectories(List<String> skillDirectories, List<String> expectedSkillNames) {
        assertEquals(1, skillDirectories.size());
        var selectedRoot = Path.of(skillDirectories.get(0));
        assertThat(Files.isDirectory(selectedRoot)).isTrue();
        for (var expectedSkillName : expectedSkillNames) {
            assertThat(Files.isRegularFile(selectedRoot.resolve(expectedSkillName).resolve("SKILL.md")))
                    .as("Missing selected skill in root: %s", expectedSkillName)
                    .isTrue();
        }
    }

    private static ChangeVerificationPromptPreparation preparation() {
        return new ChangeVerificationPromptPreparation(
                "Change Verification prompt",
                Map.of("change-verification/repository-scope.md", "# Repository Scope")
        );
    }

    private static ChangeVerificationJobStartRequest request() {
        return new ChangeVerificationJobStartRequest(
                "CRM-123",
                null,
                List.of(ChangeVerificationJobMode.CHECK_COMPLIANCE),
                true,
                true,
                "Focus contracts.",
                "sandbox-a",
                "customer-api",
                "gpt-5.4",
                "medium"
        );
    }

    private static ChangeVerificationSourceDiscoveryResult sourceDiscovery() {
        return new ChangeVerificationSourceDiscoveryResult(
                "CRM-123",
                "https://jira.example.com/browse/CRM-123",
                null,
                null,
                null,
                List.of(new ChangeVerificationRepositorySnapshot(
                        "CRM/runtime/customer-api",
                        "CRM/runtime/customer-api",
                        "customer-api",
                        "feature/CRM-123-status",
                        "main",
                        List.of(),
                        List.of(new ChangeVerificationChangedFileSnapshot(
                                "src/main/java/CustomerController.java",
                                "src/main/java/CustomerController.java",
                                "src/main/java/CustomerController.java",
                                false,
                                false,
                                false,
                                List.of("!1")
                        )),
                        List.of(new InstructionSource(
                                "CRM/runtime/customer-api",
                                "feature/CRM-123-status",
                                "AGENTS.md",
                                "AGENTS",
                                "Keep controllers thin.",
                                false,
                                null,
                                List.of("src/main/java/CustomerController.java")
                        )),
                        List.of()
                )),
                List.of()
        );
    }

    private static ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(
                name,
                name,
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
    }
}
