package pl.mkn.tdw.features.configdriftviewer.ai.copilot;

import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportToolNames;
import pl.mkn.tdw.features.configdriftviewer.ai.ConfigDriftViewerAiTestFixtures;
import pl.mkn.tdw.features.configdriftviewer.ai.preparation.ConfigDriftViewerPromptPreparation;
import pl.mkn.tdw.features.configdriftviewer.ai.report.ConfigDriftViewerReportFactory;
import pl.mkn.tdw.features.configdriftviewer.ai.report.ConfigDriftViewerReportSectionIds;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConfigDriftViewerCopilotRunRequestAssemblerTest {

    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("config-drift-viewer");

    @Test
    void shouldRejectBasicBeforeCopilotAssembly() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var assembler = assembler(toolFactory);

        assertThatThrownBy(() -> assembler.assemble(
                "run-basic",
                request(ConfigDriftViewerMode.BASIC),
                ConfigDriftViewerAiTestFixtures.deterministic(ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED),
                null,
                preparation(),
                AnalysisAiAuthRef.localToken(null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEEP");
        verifyNoInteractions(toolFactory);
    }

    @Test
    void shouldAssembleDeepWithOnlyFocusedToolsAndResolvedScopes() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var contextCaptor = ArgumentCaptor.forClass(CopilotToolSessionContext.class);
        when(toolFactory.createToolDefinitions(contextCaptor.capture(), eq(DESCRIPTION_CONTEXT)))
                .thenReturn(registeredTools());
        var assembler = assembler(toolFactory);

        var assembly = assembler.assemble(
                "run-deep",
                request(ConfigDriftViewerMode.DEEP),
                ConfigDriftViewerAiTestFixtures.deterministic(ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED),
                ConfigDriftViewerAiTestFixtures.deep(),
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
        assertThat(assembly.runRequest().sessionConfigRequest().effectiveAvailableToolNames()).contains("skill");
        var hidden = contextCaptor.getValue().hiddenContext();
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_GROUP)).isEqualTo("platform");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES))
                .isEqualTo(List.of("crm-api"));
        assertThat((List<?>) hidden.get(ConfigDriftViewerCopilotToolContextKeys.ALLOWED_REPOSITORIES))
                .singleElement()
                .asString()
                .contains("crm-api", "release-1", "src/main/java");
        assertThat(stringList(hidden.get(
                ConfigDriftViewerCopilotToolContextKeys.ALLOWED_OPERATIONAL_ENTITY_IDS
        )))
                .contains("crm-api", "customer-profile-api", "repository-1", "scope-1");
        assertThat(hidden.get(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS))
                .isEqualTo(ConfigDriftViewerReportSectionIds.aiWritable());
        assertThat(assembly.runRequest().initialReport().sections())
                .extracting(section -> section.id())
                .contains(ConfigDriftViewerReportSectionIds.OWNERSHIP_AND_HANDOFF);
    }

    private ConfigDriftViewerCopilotRunRequestAssembler assembler(CopilotSdkToolFactory toolFactory) {
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("platform");
        return new ConfigDriftViewerCopilotRunRequestAssembler(
                toolFactory,
                new ConfigDriftViewerCopilotToolSessionContextFactory(gitLabProperties),
                new CopilotRunAuthMapper(),
                new ConfigDriftViewerReportFactory()
        );
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

    private ConfigDriftViewerJobStartRequest request(
            ConfigDriftViewerMode mode
    ) {
        return new ConfigDriftViewerJobStartRequest(
                mode,
                "runtime-config",
                java.util.List.of("crm-api"),
                "dev1",
                "zt001",
                "release-1",
                "gpt-5.4",
                "medium"
        );
    }

    private ConfigDriftViewerPromptPreparation preparation() {
        return new ConfigDriftViewerPromptPreparation(
                "sanitized prompt",
                Map.of("runtime-configuration/scope.json", "{}"),
                List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        return (List<String>) value;
    }
}
