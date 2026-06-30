package pl.mkn.tdw.features.flowexplorer.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.SessionConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunAssembly;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotRunRequestAssembler;
import pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation.FlowExplorerCopilotToolAccessPolicy;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerFollowUpPromptPreparationService;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStateSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultOverview;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
import pl.mkn.tdw.features.flowexplorer.job.export.FlowExplorerExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuationException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowExplorerLocalRunChatHandlerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-20T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-06-20T10:05:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void shouldResumeCopilotSessionAndAppendCompletedAssistantResponse() throws Exception {
        var assembler = mock(FlowExplorerCopilotRunRequestAssembler.class);
        var preparationService = mock(CopilotRunPreparationService.class);
        var executionGateway = mock(CopilotSdkExecutionGateway.class);
        var tokenResolver = new CapturingAccessTokenResolver();
        var handler = new FlowExplorerLocalRunChatHandler(
                objectMapper,
                assembler,
                new FlowExplorerFollowUpPromptPreparationService(),
                preparationService,
                executionGateway,
                new CopilotRunAuthMapper(),
                tokenResolver
        );
        var runRequest = runRequest();
        var preparedSession = preparedSession(runRequest);
        var promptCaptor = ArgumentCaptor.forClass(FlowExplorerPromptPreparation.class);
        var requestCaptor = ArgumentCaptor.forClass(FlowExplorerJobStartRequest.class);
        when(assembler.assembleFollowUp(
                any(String.class),
                requestCaptor.capture(),
                any(),
                promptCaptor.capture(),
                eq("initial-session-1"),
                any(AnalysisAiAuthRef.class)
        )).thenReturn(new FlowExplorerCopilotRunAssembly(
                runRequest,
                new CopilotToolSessionContext("follow-up-1", "initial-session-1", Map.of()),
                FlowExplorerCopilotToolAccessPolicy.fromRegisteredTools(List.of())
        ));
        when(preparationService.prepare(runRequest)).thenReturn(preparedSession);
        when(executionGateway.execute(any(CopilotPreparedSession.class))).thenAnswer(invocation -> {
            var session = (CopilotPreparedSession) invocation.getArgument(0);
            session.evidenceSink().accept(section("GitLab", "follow-up-file-chunk", "Code chunk"));
            session.activitySink().accept(new AnalysisAiActivityEvent(
                    "event-1",
                    null,
                    "tool",
                    "gitlab",
                    "completed",
                    "GitLab search",
                    "Fetched code chunk",
                    "turn-1",
                    "interaction-1",
                    "tool-call-1",
                    "gitlab_get_file_chunk",
                    Instant.parse("2026-06-20T10:06:00Z"),
                    Map.of("projectName", "crm-service")
            ));
            return new CopilotExecutionResult("Odpowiedz lokalna.", null, "follow-up-session-1");
        });

        var result = handler.continueRun(
                indexEntry(),
                record(snapshot()),
                "Gdzie jest walidacja?"
        );

        assertEquals("LOCAL_TOKEN", tokenResolver.auth.mode().name());
        assertTrue(promptCaptor.getValue().prompt().contains("# Flow Explorer follow-up chat"));
        assertTrue(promptCaptor.getValue().prompt().contains("Domyslnie odpowiedz w Markdown"));
        assertTrue(promptCaptor.getValue().prompt().contains("Nie zwracaj pelnego JSON"));
        assertTrue(promptCaptor.getValue().prompt().contains("Gdzie jest walidacja?"));
        assertEquals("crm-service", requestCaptor.getValue().systemId());
        assertEquals("GET", requestCaptor.getValue().httpMethod());
        assertEquals("/api/customers/{id}", requestCaptor.getValue().endpointPath());
        verify(assembler).assembleFollowUp(
                any(String.class),
                any(FlowExplorerJobStartRequest.class),
                same(snapshot().contextSnapshot()),
                any(FlowExplorerPromptPreparation.class),
                eq("initial-session-1"),
                any(AnalysisAiAuthRef.class)
        );

        var updatedEnvelope = objectMapper.treeToValue(
                result.record().exportEnvelope(),
                FlowExplorerExportEnvelope.class
        );
        var updatedJob = updatedEnvelope.payload().job();
        assertEquals(result.updatedAt(), updatedEnvelope.exportedAt());
        assertEquals(2, updatedJob.chatMessages().size());
        assertEquals("USER", updatedJob.chatMessages().get(0).role());
        assertEquals("Gdzie jest walidacja?", updatedJob.chatMessages().get(0).content());
        assertEquals("ASSISTANT", updatedJob.chatMessages().get(1).role());
        assertEquals("Odpowiedz lokalna.", updatedJob.chatMessages().get(1).content());
        assertTrue(updatedJob.chatMessages().get(1).prompt().contains("# Flow Explorer follow-up chat"));
        assertTrue(updatedJob.chatMessages().get(1).prompt().contains("Gdzie jest walidacja?"));
        assertEquals(1, updatedJob.chatMessages().get(1).toolEvidenceSections().size());
        assertEquals(1, updatedJob.chatMessages().get(1).aiActivityEvents().size());
        assertEquals("flow-report-1", updatedJob.report().reportId());
        assertEquals("follow-up-session-1", result.record().continuation().copilotSessionId());
        assertEquals("github-copilot-sdk", result.record().continuation().copilotRuntime());
        assertEquals("copilot-session", result.record().continuation().continuationMode());
    }

    @Test
    void shouldRejectMismatchedSnapshotAndIndex() {
        var handler = new FlowExplorerLocalRunChatHandler(
                objectMapper,
                mock(FlowExplorerCopilotRunRequestAssembler.class),
                new FlowExplorerFollowUpPromptPreparationService(),
                mock(CopilotRunPreparationService.class),
                mock(CopilotSdkExecutionGateway.class),
                new CopilotRunAuthMapper(),
                auth -> new CopilotAccessToken("token", null, null, false)
        );

        var exception = assertThrows(
                LocalAnalysisRunContinuationException.class,
                () -> handler.continueRun(indexEntry("other-job"), record(snapshot()), "Dopytaj")
        );

        assertEquals(LocalAnalysisRunContinuationException.Reason.CORRUPTED, exception.reason());
    }

    private LocalAnalysisRunRecord record(FlowExplorerJobStateSnapshot snapshot) {
        return LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(FlowExplorerExportEnvelope.from(snapshot, COMPLETED_AT)),
                new LocalAnalysisRunContinuation(
                        true,
                        null,
                        "LOCAL_TOKEN",
                        "local-token",
                        "initial-session-1",
                        "github-copilot-sdk",
                        "copilot-session"
                )
        );
    }

    private LocalAnalysisRunIndexEntry indexEntry() {
        return indexEntry("flow-job-1");
    }

    private LocalAnalysisRunIndexEntry indexEntry(String analysisId) {
        return new LocalAnalysisRunIndexEntry(
                analysisId,
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + analysisId + "/run.json",
                "flow-explorer",
                "GET /api/customers/{id} / DEEP_DISCOVERY",
                "COMPLETED",
                CREATED_AT,
                COMPLETED_AT,
                COMPLETED_AT
        );
    }

    private FlowExplorerJobStateSnapshot snapshot() {
        return new FlowExplorerJobStateSnapshot(
                "flow-job-1",
                "crm-service",
                "crm-api:GET:/api/customers/{id}",
                "GET",
                "/api/customers/{id}",
                "main",
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW),
                sectionModes(),
                "gpt-5.4",
                "medium",
                "COMPLETED",
                null,
                null,
                null,
                null,
                CREATED_AT,
                COMPLETED_AT,
                COMPLETED_AT,
                List.of(),
                null,
                List.of(section("flow-explorer", "endpoint-context", "Endpoint target")),
                List.of(section("gitlab", "initial-tool", "Initial tool evidence")),
                List.of(),
                List.of(),
                List.of(),
                "Prepared prompt",
                new FlowExplorerResultResponse(
                        "COMPLETED",
                        "crm-service",
                        "crm-api:GET:/api/customers/{id}",
                        "GET",
                        "/api/customers/{id}",
                        "main",
                        FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                        "Prepared prompt",
                        aiResponse(),
                        new AnalysisAiUsage(10, 5, 0, 0, 15, 0.01, 1000, 1, "gpt-5.4", null, null, null)
                ),
                report()
        );
    }

    private AnalysisReport report() {
        return new AnalysisReport(
                "flow-report-1",
                "Flow Explorer: GET /api/customers/{id}",
                "crm-service | main | DEEP_DISCOVERY",
                "",
                List.of(
                        new AnalysisReportSection(
                                "OVERVIEW",
                                "Overview",
                                0,
                                "Overview",
                                AnalysisReportMeta.empty()
                        ),
                        new AnalysisReportSection(
                                "FUNCTIONAL_FLOW",
                                "Functional flow",
                                1,
                                "Section Functional flow",
                                AnalysisReportMeta.empty()
                        )
                ),
                AnalysisReportMeta.empty()
        );
    }

    private List<FlowExplorerResultSectionModeAssignment> sectionModes() {
        return List.of(
                new FlowExplorerResultSectionModeAssignment(
                        FlowExplorerResultSectionId.FUNCTIONAL_FLOW,
                        "Functional flow",
                        FlowExplorerResultSectionMode.DEEP
                ),
                new FlowExplorerResultSectionModeAssignment(
                        FlowExplorerResultSectionId.VALIDATIONS,
                        "Validations",
                        FlowExplorerResultSectionMode.COMPACT
                ),
                new FlowExplorerResultSectionModeAssignment(
                        FlowExplorerResultSectionId.PERSISTENCE,
                        "Persistence",
                        FlowExplorerResultSectionMode.COMPACT
                ),
                new FlowExplorerResultSectionModeAssignment(
                        FlowExplorerResultSectionId.INTEGRATIONS,
                        "Integrations",
                        FlowExplorerResultSectionMode.COMPACT
                )
        );
    }

    private FlowExplorerAiResponse aiResponse() {
        return new FlowExplorerAiResponse(
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                "business_or_system_analyst_tester",
                new FlowExplorerResultOverview("Overview", "high", List.of()),
                sectionModes().stream()
                        .map(sectionMode -> new FlowExplorerResultSection(
                                sectionMode.id(),
                                sectionMode.title(),
                                sectionMode.mode(),
                                "Section " + sectionMode.title(),
                                List.of(),
                                List.of(),
                                List.of()
                        ))
                        .toList(),
                List.of(),
                List.of(),
                List.of(),
                "high",
                List.of("Dopytaj o walidacje.")
        );
    }

    private CopilotRunRequest runRequest() {
        return new CopilotRunRequest(
                "follow-up-1",
                "Gdzie jest walidacja?",
                new CopilotSessionConfigRequest(
                        "initial-session-1",
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        "Denied"
                ),
                Map.of(),
                null
        );
    }

    private CopilotPreparedSession preparedSession(CopilotRunRequest runRequest) {
        return new CopilotPreparedSession(
                runRequest.runReference(),
                new CopilotClientOptions(),
                new SessionConfig(),
                new MessageOptions().setPrompt(runRequest.prompt()),
                runRequest.prompt(),
                runRequest.artifactContents()
        );
    }

    private static AnalysisEvidenceSection section(String provider, String category, String title) {
        return new AnalysisEvidenceSection(
                provider,
                category,
                List.of(new AnalysisEvidenceItem(
                        title,
                        List.of(new AnalysisEvidenceAttribute("key", "value"))
                ))
        );
    }

    private static final class CapturingAccessTokenResolver implements CopilotAccessTokenResolver {

        private CopilotRunAuth auth;

        @Override
        public CopilotAccessToken resolve(CopilotRunAuth auth) {
            this.auth = auth;
            return new CopilotAccessToken("token", null, null, false);
        }
    }
}
