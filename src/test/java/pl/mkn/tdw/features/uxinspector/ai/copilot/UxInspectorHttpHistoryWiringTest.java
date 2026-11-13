package pl.mkn.tdw.features.uxinspector.ai.copilot;

import com.github.copilot.rpc.ToolDefinition;
import com.github.copilot.rpc.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import pl.mkn.tdw.aiplatform.copilot.runtime.*;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.*;
import pl.mkn.tdw.aiplatform.copilot.tools.*;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolContextFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationEventPublisher;
import pl.mkn.tdw.features.uxinspector.ai.UxInspectorPromptPreparation;
import pl.mkn.tdw.features.uxinspector.ai.chat.*;
import pl.mkn.tdw.features.uxinspector.ai.tools.UxInspectorTargetToolSetFactory;
import pl.mkn.tdw.features.uxinspector.report.UxInspectorReportFactory;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.localworkspace.analysisruns.tools.*;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static pl.mkn.tdw.common.HttpHistoryTestFixtures.*;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.*;

class UxInspectorHttpHistoryWiringTest {
    private pl.mkn.tdw.features.uxinspector.job.UxInspectorAnalysisRequest request(String question) {
        return new pl.mkn.tdw.features.uxinspector.job.UxInspectorAnalysisRequest("crm-agent-portal", "main", VIEW_ID,
                question, capture(), "gpt-5.6-terra", "medium");
    }

    @Test
    void enablesHttpIndependentlyOfStoreAndKeepsActualHandlerAndActivityContentIntact() throws Exception {
        for (boolean withStore : List.of(false, true)) for (boolean withHttp : List.of(false, true)) {
            var runs = mock(LocalAnalysisRunStore.class);
            var record = LocalAnalysisRunRecord.v1(MAPPER.createObjectNode(), null);
            if (withStore) record = record.withStoreSnapshot(new LocalAnalysisRunStoreSnapshot(MAPPER.createObjectNode(), TIME, "crm", 0));
            if (withHttp) record = record.withHttpHistorySnapshot(snapshot(MAPPER.createArrayNode().add(
                    entry(1, MAPPER.createObjectNode().put("notes", "🙂漢\"\\".repeat(20000))))));
            when(runs.findById("crm-original")).thenReturn(Optional.of(record));
            var handler = new CopilotToolInvocationHandler(MAPPER, new CopilotToolContextFactory(), List.of(), mock(CopilotToolInvocationEventPublisher.class));
            var factory = new CopilotSdkToolFactory(List.of(), MAPPER, List.of(), handler);
            var assembler = new UxInspectorCopilotRunRequestAssembler(factory, new UxInspectorCopilotToolSessionContextFactory(),
                    new UxInspectorTargetToolSetFactory(), new CopilotRunAuthMapper(), new UxInspectorReportFactory(),
                    new RunStoreToolSetFactory(runs), new RunHttpHistoryToolSetFactory(runs));
            var assembly = assembler.assemble("crm-original", request("Dlaczego?"), targetContext(),
                    new UxInspectorPromptPreparation("CRM prompt", Map.of(pl.mkn.tdw.features.uxinspector.ai.UxInspectorPromptPreparationService.REPORT_ARTIFACT, "CRM report contract")), AnalysisAiAuthRef.localToken(null));
            var config = assembly.runRequest().sessionConfigRequest();
            assertThat(config.availableToolNames().contains(RunHttpHistoryTools.READ)).isEqualTo(withHttp);
            assertThat(config.availableToolNames().contains(RunStoreTools.READ)).isEqualTo(withStore);
            assertThat(config.skillsEnabled()).isEqualTo(withStore);
            if (withHttp) {
                var tool = assembly.toolAccessPolicy().enabledTools().stream().filter(t -> t.name().equals(RunHttpHistoryTools.READ)).findFirst().orElseThrow();
                var result = tool.handler().invoke(new ToolInvocation().setToolName(RunHttpHistoryTools.READ)
                        .setSessionId(assembly.toolSessionContext().copilotSessionId()).setToolCallId("crm-call")
                        .setArguments(MAPPER.valueToTree(Map.of("entryId", 1, "part", "responseBody", "pointer", "/notes")))).join();
                var serialized = MAPPER.writeValueAsString(result);
                assertThat(serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8000);
                var activity = new CopilotToolResultContentFactory(MAPPER).capture(serialized);
                assertThat(activity.truncated()).isFalse();
                assertThat(activity.value()).isEqualTo(result);
                assertThat(MAPPER.valueToTree(result).path("hasMore").asBoolean()).isTrue();
            }
        }
    }

    @Test
    void followUpBindsCallbacksToOriginalSavedRunRatherThanNewChatTurn() {
        var prompts = mock(UxInspectorFollowUpPromptService.class);
        when(prompts.prepare(any())).thenReturn("CRM follow-up");
        var store = mock(RunStoreToolSetFactory.class);
        var http = mock(RunHttpHistoryToolSetFactory.class);
        var callback = mock(ToolCallback.class);
        when(http.create("crm-original")).thenReturn(List.of(callback));
        var factory = mock(CopilotSdkToolFactory.class);
        var names = new ArrayList<>(List.of(
                "uxi_list_target_candidates", "uxi_read_target_slice",
                "gitlab_read_frontend_route_branch_slice", "gitlab_read_frontend_typescript_symbol_slice",
                "gitlab_list_repository_tree", "gitlab_list_repository_files", "gitlab_search_repository_files",
                "gitlab_read_repository_file", "gitlab_read_repository_file_chunk", "gitlab_read_openapi_endpoint_slice", RunHttpHistoryTools.READ));
        when(factory.createToolDefinitions(any(), any(), anyList())).thenReturn(names.stream().map(name ->
                ToolDefinition.createSkipPermission(name, name, Map.of(), invocation -> CompletableFuture.completedFuture(Map.of()))).toList());
        var prepare = mock(CopilotRunPreparationService.class);
        var prepared = mock(CopilotPreparedSession.class);
        when(prepare.prepare(any())).thenReturn(prepared);
        var execution = mock(CopilotSdkExecutionGateway.class);
        when(execution.execute(prepared)).thenReturn(new CopilotExecutionResult("CRM answer", null));
        var service = new UxInspectorFollowUpChatService(prompts, new UxInspectorCopilotToolSessionContextFactory(),
                new UxInspectorTargetToolSetFactory(), store, http, factory, new CopilotRunAuthMapper(), prepare, execution);
        service.chat(new UxInspectorFollowUpChatRequest("crm-chat-turn", request("Dlaczego?"), targetContext(), "CRM question",
                "crm-session", AnalysisAiAuthRef.localToken(null), null, "crm-original"), null, null);
        verify(http).create("crm-original");
        verify(http, never()).create("crm-chat-turn");
        verify(factory).createToolDefinitions(any(), any(), argThat(callbacks -> callbacks.contains(callback)));
        var run = org.mockito.ArgumentCaptor.forClass(CopilotRunRequest.class);
        verify(prepare).prepare(run.capture());
        assertThat(run.getValue().sessionConfigRequest().skillsEnabled()).isFalse();
        assertThat(run.getValue().sessionConfigRequest().durableSystemInstructions()).contains("run_http_history_read_value");
    }
}
