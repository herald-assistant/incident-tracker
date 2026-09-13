package pl.mkn.tdw.features.operationalcontextassistance.ai;

import org.junit.jupiter.api.Test;
import com.github.copilot.rpc.ToolDefinition;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotContextTierPreference;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotSessionHardToolBudget;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceSnapshot;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_FILES;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_BRANCHES;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_TREE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.SEARCH_REPOSITORY_FILES;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE;

class OperationalContextAssistanceCopilotRunRequestAssemblerTest {

    @Test
    void assemblesNoSourceSessionWithRequiredLongContext() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var assembler = new OperationalContextAssistanceCopilotRunRequestAssembler(
                new CopilotRunAuthMapper(), toolFactory, new OperationalContextAssistanceAiProperties(),
                new GitLabProperties());
        var assembly = assembler.assemble(
                "opctx-job-1",
                new AnalysisAiOptions("gpt-5.4-mini", "high"),
                AnalysisAiAuthRef.localToken("test"),
                new OperationalContextAssistancePromptPreparation(
                        "effective Polish skill and JSON material",
                        Map.of("operational-context-assistance/input.json", "{}"),
                        Set.of("operator:description")
                ),
                null
        );
        var request = assembly.runRequest();

        assertThat(request.sessionTarget().existing()).isFalse();
        assertThat(request.sessionConfigRequest().tools()).isEmpty();
        assertThat(request.sessionConfigRequest().availableToolNames()).isEmpty();
        assertThat(request.sessionConfigRequest().effectiveAvailableToolNames()).isEmpty();
        assertThat(request.sessionConfigRequest().skillsEnabled()).isFalse();
        assertThat(request.sessionConfigRequest().skillToolAvailable()).isFalse();
        assertThat(request.sessionConfigRequest().contextTierPreference())
                .isEqualTo(CopilotContextTierPreference.LONG_CONTEXT_REQUIRED);
        assertThat(request.initialReport()).isNull();
        assertThat(request.artifactContents()).containsEntry("operational-context-assistance/input.json", "{}");
        assertThat(assembly.sourceScope()).isNull();
        org.mockito.Mockito.verifyNoInteractions(toolFactory);
    }

    @Test
    void bindsGeneralGitLabToolsToSelectedProjectAndMainGroup() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        when(toolFactory.createToolDefinitions(any(), any())).thenReturn(List.of(
                tool(LIST_REPOSITORY_BRANCHES), tool(LIST_REPOSITORY_TREE), tool(LIST_REPOSITORY_FILES),
                tool(SEARCH_REPOSITORY_FILES),
                tool(READ_REPOSITORY_FILE), tool("gitlab_find_flow_context"), tool("opctx_get_entity")
        ));
        var properties = new OperationalContextAssistanceAiProperties();
        properties.setModel("feature-model");
        properties.setReasoningEffort("xhigh");
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("CRM");
        var assembler = new OperationalContextAssistanceCopilotRunRequestAssembler(
                new CopilotRunAuthMapper(), toolFactory, properties, gitLabProperties);
        var source = new OperationalContextGitLabSourceSnapshot(
                "PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS",
                new OperationalContextGitLabSourceSnapshot.RepositoryGit(
                        "gitlab", "CRM/PROCESSES", "CRM_CUSTOMER_PROFILE_PROCESS",
                        "CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS", "https://gitlab.example/CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS"
                ),
                "main", "1234567890abcdef1234567890abcdef12345678", List.of(), List.of()
        );

        var assembly = assembler.assemble(
                "opctx-job-2", AnalysisAiOptions.DEFAULT, AnalysisAiAuthRef.localToken("test"),
                new OperationalContextAssistancePromptPreparation("prompt", Map.of(), Set.of()), source
        );
        var request = assembly.runRequest();
        var context = ArgumentCaptor.forClass(CopilotToolSessionContext.class);
        verify(toolFactory).createToolDefinitions(context.capture(), any());

        assertThat(request.sessionConfigRequest().tools()).extracting(ToolDefinition::name)
                .containsExactlyInAnyOrder(LIST_REPOSITORY_BRANCHES, LIST_REPOSITORY_TREE, LIST_REPOSITORY_FILES,
                        SEARCH_REPOSITORY_FILES, READ_REPOSITORY_FILE);
        assertThat(request.sessionConfigRequest().availableToolNames())
                .containsExactlyInAnyOrder(LIST_REPOSITORY_BRANCHES, LIST_REPOSITORY_TREE, LIST_REPOSITORY_FILES,
                        SEARCH_REPOSITORY_FILES, READ_REPOSITORY_FILE);
        assertThat(request.sessionConfigRequest().skillsEnabled()).isTrue();
        assertThat(request.sessionConfigRequest().effectiveAvailableToolNames())
                .containsExactlyInAnyOrder(LIST_REPOSITORY_BRANCHES, LIST_REPOSITORY_TREE, LIST_REPOSITORY_FILES,
                        SEARCH_REPOSITORY_FILES, READ_REPOSITORY_FILE, "skill")
                .doesNotContain("gitlab_find_flow_context", "opctx_get_entity", "shell", "terminal", "filesystem");
        assertThat(request.sessionConfigRequest().modelSelection().model()).isEqualTo("feature-model");
        assertThat(request.sessionConfigRequest().modelSelection().reasoningEffort()).isEqualTo("xhigh");
        assertThat(assembly.sourceScope().selectedProjectPath()).isEqualTo("CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS");
        assertThat(assembly.sourceScope().selectedCommit()).isEqualTo(source.commitId());
        assertThat(context.getValue().hiddenContext().values()).contains(assembly.sourceScope());
        assertThat(context.getValue().hiddenContext().get(AgentToolContextKeys.TOOL_HARD_BUDGET))
                .isInstanceOf(CopilotSessionHardToolBudget.class);
    }

    private ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(
                name, "test", Map.of("type", "object", "properties", Map.of()),
                invocation -> java.util.concurrent.CompletableFuture.completedFuture(Map.of())
        );
    }
}
