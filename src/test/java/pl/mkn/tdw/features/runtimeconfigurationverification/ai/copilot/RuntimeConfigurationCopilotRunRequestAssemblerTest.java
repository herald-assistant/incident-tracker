package pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotNamedSkillDirectoryResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportToolNames;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiTestFixtures;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparation;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.report.RuntimeConfigurationReportFactory;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.report.RuntimeConfigurationReportSectionIds;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuntimeConfigurationCopilotRunRequestAssemblerTest {

    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("runtime-configuration-verification");

    @TempDir
    Path tempDirectory;

    @Test
    void shouldRejectBasicBeforeCopilotAssembly() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var assembler = assembler(toolFactory);

        assertThatThrownBy(() -> assembler.assemble(
                "run-basic",
                request(RuntimeConfigurationVerificationMode.BASIC),
                RuntimeConfigurationAiTestFixtures.deterministic(RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED),
                null,
                preparation(),
                AnalysisAiAuthRef.localToken(null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEEP");
        verifyNoInteractions(toolFactory);
    }

    @Test
    void shouldAssembleDeepWithOnlyFocusedToolsResolvedScopesAndDeepSkill() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var contextCaptor = ArgumentCaptor.forClass(CopilotToolSessionContext.class);
        when(toolFactory.createToolDefinitions(contextCaptor.capture(), eq(DESCRIPTION_CONTEXT)))
                .thenReturn(registeredTools());
        var assembler = assembler(toolFactory);

        var assembly = assembler.assemble(
                "run-deep",
                request(RuntimeConfigurationVerificationMode.DEEP),
                RuntimeConfigurationAiTestFixtures.deterministic(RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED),
                RuntimeConfigurationAiTestFixtures.deep(),
                preparation(),
                AnalysisAiAuthRef.localToken(null)
        );

        assertThat(assembly.toolAccessPolicy().availableToolNames())
                .contains(
                        GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                        GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                        OperationalContextToolNames.GET_ENTITY
                )
                .doesNotContain(GitLabToolNames.READ_REPOSITORY_FILE, "db_describe_table");
        assertSelectedSkill(
                assembly.runRequest().sessionConfigRequest().skillDirectories(),
                RuntimeConfigurationCopilotRuntimeSkillNames.DEEP_REVIEW
        );
        var hidden = contextCaptor.getValue().hiddenContext();
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_GROUP)).isEqualTo("platform");
        assertThat((List<?>) hidden.get(RuntimeConfigurationCopilotToolContextKeys.ALLOWED_REPOSITORIES))
                .singleElement()
                .asString()
                .contains("crm-api", "release-1", "src/main/java");
        assertThat(stringList(hidden.get(
                RuntimeConfigurationCopilotToolContextKeys.ALLOWED_OPERATIONAL_ENTITY_IDS
        )))
                .contains("crm-api", "customer-profile-api", "repository-1", "scope-1");
        assertThat(hidden.get(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS))
                .isEqualTo(RuntimeConfigurationReportSectionIds.aiWritable());
        assertThat(assembly.runRequest().initialReport().sections())
                .extracting(section -> section.id())
                .contains(RuntimeConfigurationReportSectionIds.OWNERSHIP_AND_HANDOFF);
    }

    private RuntimeConfigurationCopilotRunRequestAssembler assembler(CopilotSdkToolFactory toolFactory) {
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("platform");
        return new RuntimeConfigurationCopilotRunRequestAssembler(
                toolFactory,
                new RuntimeConfigurationCopilotToolSessionContextFactory(gitLabProperties),
                skillDirectoryResolver(),
                new CopilotRunAuthMapper(),
                new RuntimeConfigurationReportFactory()
        );
    }

    private CopilotNamedSkillDirectoryResolver skillDirectoryResolver() {
        var properties = new CopilotSdkProperties();
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        return new CopilotNamedSkillDirectoryResolver(new CopilotSkillRuntimeLoader(properties));
    }

    private List<ToolDefinition> registeredTools() {
        return List.of(
                tool(CopilotReportToolNames.GET_CURRENT),
                tool(CopilotReportToolNames.UPSERT_SECTION),
                tool(CopilotReportToolNames.UPDATE_HEADER),
                tool(CopilotReportToolNames.UPDATE_META),
                tool(GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES),
                tool(GitLabToolNames.READ_REPOSITORY_FILE),
                tool(GitLabToolNames.READ_REPOSITORY_FILE_CHUNK),
                tool(OperationalContextToolNames.GET_SCOPE),
                tool(OperationalContextToolNames.GET_ENTITY),
                tool("db_describe_table")
        );
    }

    private ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(
                name,
                name,
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
    }

    private RuntimeConfigurationVerificationJobStartRequest request(
            RuntimeConfigurationVerificationMode mode
    ) {
        return new RuntimeConfigurationVerificationJobStartRequest(
                mode,
                "runtime-config",
                "crm-api",
                "dev1",
                "zt001",
                "release-1",
                "gpt-5.4",
                "medium"
        );
    }

    private RuntimeConfigurationPromptPreparation preparation() {
        return new RuntimeConfigurationPromptPreparation(
                "sanitized prompt",
                Map.of("runtime-configuration/scope.json", "{}"),
                List.of()
        );
    }

    private void assertSelectedSkill(List<String> directories, String skillName) {
        assertThat(directories).singleElement().satisfies(directory ->
                assertThat(Files.isRegularFile(Path.of(directory).resolve(skillName).resolve("SKILL.md"))).isTrue()
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        return (List<String>) value;
    }
}
