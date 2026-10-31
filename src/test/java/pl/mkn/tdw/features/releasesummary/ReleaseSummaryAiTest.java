package pl.mkn.tdw.features.releasesummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.releasesummary.ai.*;
import pl.mkn.tdw.features.releasesummary.report.*;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.aiplatform.copilot.runtime.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.*;
import pl.mkn.tdw.aiplatform.copilot.tools.*;
import pl.mkn.tdw.aiplatform.copilot.tools.context.*;
import pl.mkn.tdw.aiplatform.copilot.tools.events.*;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.*;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.*;
import pl.mkn.tdw.aiplatform.copilot.tools.report.*;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRef;
import pl.mkn.tdw.integrations.operationalcontext.contract.*;
import pl.mkn.tdw.shared.ai.*;
import pl.mkn.tdw.shared.ai.report.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseSummaryAiTest {
    final ObjectMapper mapper=new ObjectMapper();
    final ReleaseSummaryReportService reports=new ReleaseSummaryReportService(mapper);
    ReleaseSummaryAiContext context(java.util.function.Consumer<AnalysisReport> checkpoint,boolean writable) {
        return new ReleaseSummaryAiContext(ReleaseSummaryReportTest.SCOPE,ReleaseSummaryReportTest.evidence(true),
                OperationalContextReadSession.fromSnapshot(OperationalContextSnapshot.local(OperationalContextDtos.OperationalContextCatalog.empty())),
                GitLabRepositoryToolScope.forRevisions("CRM",List.of(new GitLabRepositoryToolScope.PinnedRevision("customer","refs/heads/current","a".repeat(40)),
                        new GitLabRepositoryToolScope.PinnedRevision("customer","refs/tags/next","b".repeat(40)))),
                Set.of(ReleaseSummaryReportTest.SOURCE),checkpoint,()->{},writable);
    }
    @Test void assemblyUsesExplicitReadReportToolsFrozenScopeAndGoalDrivenWithoutHardBudget() {
        var factory=mock(CopilotSdkToolFactory.class);
        var registered=new ArrayList<ToolDefinition>();
        for(var name:ReleaseSummaryToolPolicy.ALLOWED) {var tool=mock(ToolDefinition.class);when(tool.name()).thenReturn(name);registered.add(tool);}
        var shell=mock(ToolDefinition.class);when(shell.name()).thenReturn("shell");registered.add(shell);
        when(factory.createToolDefinitions(any(),any())).thenReturn(registered);
        var runner=new ReleaseSummaryAiRunner(factory,mock(CopilotRunAuthMapper.class),mock(CopilotRunPreparationService.class),
                mock(CopilotSdkExecutionGateway.class),mock(CopilotInitialContextModelSelector.class),new CopilotSdkProperties(),reports);
        var context=context(r->{},true);
        var request=new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,new GitLabRef(GitLabRef.Kind.BRANCH,"current"),
                new GitLabRef(GitLabRef.Kind.TAG,"next"),"crm-model",null);
        var run=runner.assemble("crm-run",request,"full prompt",AnalysisAiAuthRef.localToken("CRM"),context,
                reports.scaffold("report",context.scope,context.evidence),null,s->{},e->{});
        assertEquals(ReleaseSummaryToolPolicy.ALLOWED,new HashSet<>(run.sessionConfigRequest().availableToolNames()));
        assertTrue(run.sessionConfigRequest().skillsEnabled());
        var captor=ArgumentCaptor.forClass(CopilotToolSessionContext.class);verify(factory).createToolDefinitions(captor.capture(),any());
        var hidden=captor.getValue().hiddenContext();
        assertSame(context.catalog,hidden.get(AgentToolContextKeys.OPERATIONAL_CONTEXT_READ_SESSION));
        assertSame(context.repositoryScope,hidden.get(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE));
        assertFalse(hidden.containsKey(AgentToolContextKeys.TOOL_HARD_BUDGET));
        assertEquals("GOAL_DRIVEN",hidden.get(AgentToolContextKeys.TOOL_BUDGET_POLICY));
        var registry=mock(CopilotToolBudgetRegistry.class);var policy=new CopilotToolBudgetPolicy(registry);
        for(int i=0;i<1000;i++)policy.beforeInvocation(new CopilotToolInvocationPolicyRequest(captor.getValue(),"session","call","gitlab_read_repository_file","{}"));
        verifyNoInteractions(registry);
    }
    @Test void policyRejectsUnreadSourcesAndReadOnlyMutationsAndCheckpointsSuccessfulWrites() throws Exception {
        var saved=new ArrayList<AnalysisReport>();var context=context(saved::add,true);
        var store=new CopilotReportSessionStore();var report=ReleaseSummaryReportTest.completed(reports,true);store.register(report);
        var policy=new ReleaseSummaryToolPolicy(mapper,reports,store);
        var session=new CopilotToolSessionContext("run","session",Map.of(ReleaseSummaryAiContext.KEY,context,AgentToolContextKeys.REPORT_ID,"report"));
        var detail=report.sections().get(4);
        var valid=mapper.writeValueAsString(Map.of("id",detail.id(),"markdown",detail.markdown()));
        assertDoesNotThrow(()->policy.beforeInvocation(new CopilotToolInvocationPolicyRequest(session,"session","call","report_upsert_section",valid)));
        assertThrows(CopilotToolInvocationRejectedException.class,()->policy.beforeInvocation(new CopilotToolInvocationPolicyRequest(session,"session","call","report_upsert_section",
                valid.replace(ReleaseSummaryReportTest.SOURCE,"gitlab:CRM/unknown@main:fake.xml"))));
        policy.completed(new CopilotToolInvocationFinishedEvent(session,"session","call","report_upsert_section",valid,CopilotToolInvocationOutcome.COMPLETED,"{\"status\":\"ok\"}",1,null));
        assertEquals(List.of(report),saved);assertTrue(context.reportMutated);
        var readOnly=new CopilotToolSessionContext("run","session",Map.of(ReleaseSummaryAiContext.KEY,context(r->{},false)));
        assertThrows(CopilotToolInvocationRejectedException.class,()->policy.beforeInvocation(new CopilotToolInvocationPolicyRequest(readOnly,"session","call","report_update_header","{}")));
    }
}

