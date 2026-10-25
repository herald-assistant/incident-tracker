package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotContextTierPreference;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportToolNames;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerArtifactService;
import pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerPromptPreparationService;
import pl.mkn.tdw.features.uiexplorer.ai.readiness.UiExplorerAiReadinessGate;
import pl.mkn.tdw.features.uiexplorer.report.UiExplorerReportFactory;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerCopilotRunRequestAssemblerTest {

    private static final CopilotToolDescriptionContext DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("ui-explorer");

    @Test
    void shouldExposeResearchAndReportToolsForFollowUp() {
        var policy = UiExplorerCopilotToolAccessPolicy.forFollowUp(registeredTools());

        assertThat(policy.fallbackAvailable()).isTrue();
        assertThat(policy.availableToolNames()).contains(
                GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                GitLabToolNames.READ_REPOSITORY_FILE,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK
        );
        assertThat(policy.availableToolNames()).containsAll(CopilotReportToolNames.allToolNames());
        assertThat(policy.availableToolNames()).contains(
                OperationalContextToolNames.GET_SCOPE, OperationalContextToolNames.LIST_ENTITIES,
                OperationalContextToolNames.SEARCH, OperationalContextToolNames.GET_ENTITY);
        assertThat(UiExplorerDurableSystemInstructions.followUp())
                .contains(UiExplorerDurableSystemInstructions.operationalContextGuidance());
    }

    @Test
    void shouldAssemblePartialCrmRunWithCatalogKnowledgeAndScopedCodeTools() {
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var contextCaptor = ArgumentCaptor.forClass(CopilotToolSessionContext.class);
        when(toolFactory.createToolDefinitions(contextCaptor.capture(), eq(DESCRIPTION_CONTEXT)))
                .thenReturn(registeredTools());
        var preparation = new UiExplorerPromptPreparationService(
                new UiExplorerArtifactService(new ObjectMapper()),
                new CopilotArtifactContentMapper()
        ).prepare(request(), context());
        var readiness = new UiExplorerAiReadinessGate().evaluate(request(), context());
        var assembler = new UiExplorerCopilotRunRequestAssembler(
                toolFactory,
                new UiExplorerCopilotToolSessionContextFactory(),
                new CopilotRunAuthMapper(),
                new UiExplorerReportFactory()
        );

        var assembly = assembler.assemble(
                "crm-ui-run-1",
                request(),
                context(),
                preparation,
                readiness,
                AnalysisAiAuthRef.localToken(null)
        );

        assertThat(assembly.toolAccessPolicy().availableToolNames()).contains(
                GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE,
                GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                GitLabToolNames.READ_REPOSITORY_FILE,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK
        );
        assertThat(assembly.toolAccessPolicy().availableToolNames())
                .containsAll(CopilotReportToolNames.initialToolNames());
        assertThat(assembly.toolAccessPolicy().reportToolsAvailable()).isTrue();
        assertThat(assembly.runRequest().sessionConfigRequest().availableToolNames()).contains(
                OperationalContextToolNames.GET_SCOPE, OperationalContextToolNames.LIST_ENTITIES,
                OperationalContextToolNames.SEARCH, OperationalContextToolNames.GET_ENTITY);
        assertThat(preparation.prompt()).contains(UiExplorerDurableSystemInstructions.operationalContextGuidance());
        assertThat(assembly.runRequest().sessionConfigRequest().effectiveAvailableToolNames()).contains("skill");
        assertThat(assembly.runRequest().sessionConfigRequest().availableToolNames())
                .doesNotContain("gitlab_list_available_repositories", "db_describe_table");
        assertThat(assembly.runRequest().sessionConfigRequest().contextTierPreference())
                .isEqualTo(CopilotContextTierPreference.AUTO);
        assertThat(assembly.runRequest().artifactContents()).hasSize(7);
        assertThat(assembly.runRequest().artifactContents())
                .containsKey("ui-explorer/functional-writing-contract.md");
        assertThat(assembly.runRequest().initialReport()).isNotNull();
        assertThat(assembly.runRequest().initialReport().reportId())
                .isEqualTo("ui-explorer-report-crm-ui-run-1");
        assertThat(assembly.runRequest().initialReport().header())
                .isEqualTo("/contacts/:contactId/preferences");
        assertThat(assembly.runRequest().initialReport().sections())
                .extracting(section -> section.id())
                .containsExactly("OVERVIEW", "FORMS_AND_RULES");
        var durableInstructions = assembly.runRequest().sessionConfigRequest().durableSystemInstructions();
        assertThat(durableInstructions)
                .contains(UiExplorerDurableSystemInstructions.operationalContextGuidance())
                .contains("nieaktualny i niekompletny", "pierwszenstwo ma zweryfikowany kod odczytanej rewizji",
                        "Brak wpisu nie dowodzi braku funkcji")
                .contains("Zrodlem prawdy initial result jest `AnalysisReport`")
                .contains("`report_get_current`")
                .contains(preparation.artifactContents().get(UiExplorerArtifactService.SCREEN_CATALOG_ENTRY_ARTIFACT))
                .contains(preparation.artifactContents().get(UiExplorerArtifactService.REPORT_CONTRACT_ARTIFACT))
                .doesNotContain(preparation.artifactContents().get(UiExplorerArtifactService.SOURCE_SLICES_ARTIFACT));
        assertThat(durableInstructions.length())
                .as("durable contract must not duplicate the source research context")
                .isLessThan(10_000);
        var hidden = contextCaptor.getValue().hiddenContext();
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_GROUP)).isEqualTo("synthetic-crm");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_BRANCH)).isEqualTo("main");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES))
                .isEqualTo(List.of("crm-agent-portal"));
        assertThat(hidden.get(AgentToolContextKeys.TOOL_BUDGET_POLICY))
                .isEqualTo(AgentToolContextKeys.TOOL_BUDGET_POLICY_GOAL_DRIVEN);
        assertThat(hidden.get(AgentToolContextKeys.REPORT_ID)).isEqualTo("ui-explorer-report-crm-ui-run-1");
        assertThat(hidden.get(AgentToolContextKeys.REPORT_FEATURE)).isEqualTo("ui-explorer");
        assertThat(hidden.get(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS))
                .isEqualTo(List.of("OVERVIEW", "FORMS_AND_RULES"));
        assertThat(hidden.get(GitLabFrontendToolContextKeys.PROJECT_NAME)).isEqualTo("crm-agent-portal");
        assertThat(hidden.get(GitLabFrontendToolContextKeys.PATH_PREFIXES))
                .isEqualTo(List.of("apps/crm-agent"));
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_BRANCH))
                .isEqualTo("main");
        assertThat(hidden.get(GitLabFrontendToolContextKeys.SCREEN_SLICE_REF))
                .isEqualTo("crm-contact-preferences");
        assertThat(hidden).doesNotContainKeys("gitLabFrontendTypeScriptSliceTargets",
                "gitLabFrontendTypeScriptImportTargets");
        assertThat(preparation.prompt()).doesNotContain("synthetic-crm");
    }

    private List<ToolDefinition> registeredTools() {
        var tools = new java.util.ArrayList<ToolDefinition>(List.of(
                tool(GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE),
                tool(GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE),
                tool(GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES),
                tool(GitLabToolNames.READ_REPOSITORY_FILE),
                tool(GitLabToolNames.READ_REPOSITORY_FILE_CHUNK),
                tool(GitLabToolNames.LIST_AVAILABLE_REPOSITORIES),
                tool("db_describe_table")
        ));
        tools.addAll(List.of(OperationalContextToolNames.GET_SCOPE, OperationalContextToolNames.LIST_ENTITIES,
                OperationalContextToolNames.SEARCH, OperationalContextToolNames.GET_ENTITY).stream().map(this::tool).toList());
        CopilotReportToolNames.allToolNames().forEach(name -> tools.add(tool(name)));
        return List.copyOf(tools);
    }

    @ParameterizedTest
    @EnumSource(value = UiExplorerSectionMode.class, names = {"COMPACT", "DEEP"})
    void shouldOfferTheSameCatalogWithoutChangingSectionModeOrRequiringCatalogRegistration(UiExplorerSectionMode mode) {
        var base = request();
        var section = mode == UiExplorerSectionMode.COMPACT
                ? UiExplorerSectionId.OVERVIEW : UiExplorerSectionId.FORMS_AND_RULES;
        var request = new UiExplorerJobStartRequest(base.systemId(), base.branch(), base.screenId(),
                Map.of(section, mode), base.scenarioDescription(), base.model(), base.reasoningEffort());
        var toolFactory = mock(CopilotSdkToolFactory.class);
        var preparation = new UiExplorerPromptPreparationService(new UiExplorerArtifactService(new ObjectMapper()),
                new CopilotArtifactContentMapper()).prepare(request, context());
        var assembler = new UiExplorerCopilotRunRequestAssembler(toolFactory,
                new UiExplorerCopilotToolSessionContextFactory(), new CopilotRunAuthMapper(), new UiExplorerReportFactory());
        var readiness = new UiExplorerAiReadinessGate().evaluate(request, context());
        when(toolFactory.createToolDefinitions(org.mockito.ArgumentMatchers.any(), eq(DESCRIPTION_CONTEXT)))
                .thenReturn(registeredTools());
        var assembly = assembler.assemble("crm-catalog-mode", request, context(), preparation, readiness,
                AnalysisAiAuthRef.localToken(null));
        assertThat(assembly.runRequest().sessionConfigRequest().availableToolNames())
                .contains(OperationalContextToolNames.GET_ENTITY);
        assertThat(assembly.runRequest().initialReport().sections()).extracting(resultSection -> resultSection.id())
                .containsExactly(section.name());
        assertThat(request.sectionModes()).containsExactly(Map.entry(section, mode));

        var withoutCatalog = registeredTools().stream()
                .filter(tool -> !tool.name().startsWith(OperationalContextToolNames.PREFIX)).toList();
        assertThat(UiExplorerCopilotToolAccessPolicy.fromRegisteredTools(withoutCatalog, false).fallbackAvailable()).isTrue();
        assertThat(UiExplorerCopilotToolAccessPolicy.forFollowUp(withoutCatalog).reportToolsAvailable()).isTrue();
    }

    private ToolDefinition tool(String name) {
        return ToolDefinition.createSkipPermission(
                name,
                name,
                Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
    }
}
