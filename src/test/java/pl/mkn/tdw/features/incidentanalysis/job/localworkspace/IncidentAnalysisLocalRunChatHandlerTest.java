package pl.mkn.tdw.features.incidentanalysis.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatProvider;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatResponse;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisResultResponse;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;
import pl.mkn.tdw.features.incidentanalysis.job.export.IncidentAnalysisExportEnvelope;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuationException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisChatMessageResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentAnalysisLocalRunChatHandlerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-20T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-06-20T10:05:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void shouldRehydrateIncidentChatRequestAndAppendCompletedAssistantResponse() throws Exception {
        var provider = new CapturingChatProvider();
        var tokenResolver = new CapturingAccessTokenResolver();
        var handler = new IncidentAnalysisLocalRunChatHandler(
                objectMapper,
                provider,
                new CopilotRunAuthMapper(),
                tokenResolver
        );

        var result = handler.continueRun(
                indexEntry(),
                record(snapshotWithPreviousChat()),
                "Dopytaj o repo."
        );

        assertEquals("corr-123", provider.request.correlationId());
        assertEquals("dev3", provider.request.environment());
        assertEquals("main", provider.request.gitLabBranch());
        assertEquals("CRM/runtime", provider.request.gitLabGroup());
        assertEquals("Dopytaj o repo.", provider.request.message());
        assertEquals("initial-session-1", provider.request.copilotSessionId());
        assertEquals("gpt-5-mini", provider.request.options().model());
        assertEquals("medium", provider.request.options().reasoningEffort());
        assertEquals("LOCAL_TOKEN", provider.request.authRef().mode());
        assertEquals(1, provider.request.evidenceSections().size());
        assertEquals(2, provider.request.toolEvidenceSections().size());
        assertEquals(2, provider.request.history().size());
        assertEquals("user", provider.request.history().get(0).role());
        assertEquals("assistant", provider.request.history().get(1).role());
        assertEquals("LOCAL_TOKEN", tokenResolver.auth.mode().name());

        var updatedEnvelope = objectMapper.treeToValue(
                result.record().exportEnvelope(),
                IncidentAnalysisExportEnvelope.class
        );
        var updatedJob = updatedEnvelope.payload().job();
        assertEquals(result.updatedAt(), updatedEnvelope.exportedAt());
        assertEquals(4, updatedJob.chatMessages().size());
        assertEquals("USER", updatedJob.chatMessages().get(2).role());
        assertEquals("Dopytaj o repo.", updatedJob.chatMessages().get(2).content());
        assertEquals("ASSISTANT", updatedJob.chatMessages().get(3).role());
        assertEquals("Odpowiedz lokalna.", updatedJob.chatMessages().get(3).content());
        assertEquals(1, updatedJob.chatMessages().get(3).toolEvidenceSections().size());
        assertEquals(1, updatedJob.chatMessages().get(3).aiActivityEvents().size());
        assertEquals("incident-report-1", updatedJob.report().reportId());
        assertEquals("follow-up-session-1", result.record().continuation().copilotSessionId());
        assertEquals("github-copilot-sdk", result.record().continuation().copilotRuntime());
        assertEquals("copilot-session", result.record().continuation().continuationMode());
    }

    @Test
    void shouldReportProviderFailureWithoutReturningUpdatedRecord() {
        var provider = new CapturingChatProvider(new RuntimeException("Copilot failed."));
        var handler = new IncidentAnalysisLocalRunChatHandler(
                objectMapper,
                provider,
                new CopilotRunAuthMapper(),
                auth -> new CopilotAccessToken("token", null, null, false)
        );

        var exception = assertThrows(
                LocalAnalysisRunContinuationException.class,
                () -> handler.continueRun(indexEntry(), record(snapshotWithPreviousChat()), "Dopytaj")
        );

        assertEquals(LocalAnalysisRunContinuationException.Reason.CHAT_FAILED, exception.reason());
    }

    @Test
    void shouldRejectMismatchedSnapshotAndIndex() {
        var handler = new IncidentAnalysisLocalRunChatHandler(
                objectMapper,
                new CapturingChatProvider(),
                new CopilotRunAuthMapper(),
                auth -> new CopilotAccessToken("token", null, null, false)
        );

        var exception = assertThrows(
                LocalAnalysisRunContinuationException.class,
                () -> handler.continueRun(indexEntry("analysis-other"), record(snapshotWithPreviousChat()), "Dopytaj")
        );

        assertEquals(LocalAnalysisRunContinuationException.Reason.CORRUPTED, exception.reason());
    }

    private LocalAnalysisRunRecord record(AnalysisJobStateSnapshot snapshot) {
        return LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(IncidentAnalysisExportEnvelope.from(snapshot, COMPLETED_AT)),
                new LocalAnalysisRunContinuation(
                        true,
                        "CRM/runtime",
                        "LOCAL_TOKEN",
                        "local-token",
                        "initial-session-1",
                        "github-copilot-sdk",
                        "copilot-session"
                )
        );
    }

    private AnalysisJobStateSnapshot snapshotWithPreviousChat() {
        return new AnalysisJobStateSnapshot(
                "analysis-1",
                "corr-123",
                "gpt-5-mini",
                "medium",
                "COMPLETED",
                null,
                null,
                "dev3",
                "main",
                null,
                null,
                CREATED_AT,
                COMPLETED_AT,
                COMPLETED_AT,
                List.of(),
                List.of(section("Elastic", "logs", "Initial log")),
                List.of(section("GitLab", "initial-tool", "Initial tool evidence")),
                List.of(),
                List.of(),
                List.of(
                        message("chat-user-1", "USER", "COMPLETED", "Przygotuj opis.", List.of()),
                        message("chat-assistant-1", "ASSISTANT", "COMPLETED", "Opis gotowy.", List.of(
                                section("DB", "chat-tool", "Previous chat tool evidence")
                        ))
                ),
                "Initial prompt",
                new AnalysisResultResponse(
                        "COMPLETED",
                        "corr-123",
                        "dev3",
                        "main",
                        "DOWNSTREAM_TIMEOUT",
                        "Catalog",
                        "Catalog Context",
                        "Catalog Team",
                        "Analiza funkcjonalna.",
                        "Analiza techniczna.",
                        "medium",
                        List.of("Brak logow downstream."),
                        "Initial prompt"
                ),
                report()
        );
    }

    private AnalysisReport report() {
        return new AnalysisReport(
                "incident-report-1",
                "DOWNSTREAM_TIMEOUT",
                "dev3 | main",
                "",
                List.of(
                        new AnalysisReportSection(
                                "FUNCTIONAL_ANALYSIS",
                                "Functional analysis",
                                1,
                                "Analiza funkcjonalna.",
                                AnalysisReportMeta.empty()
                        ),
                        new AnalysisReportSection(
                                "TECHNICAL_HANDOFF",
                                "Technical handoff",
                                2,
                                "Analiza techniczna.",
                                AnalysisReportMeta.empty()
                        )
                ),
                AnalysisReportMeta.empty()
        );
    }

    private LocalAnalysisRunIndexEntry indexEntry() {
        return indexEntry("analysis-1");
    }

    private LocalAnalysisRunIndexEntry indexEntry(String analysisId) {
        return new LocalAnalysisRunIndexEntry(
                analysisId,
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + analysisId + "/run.json",
                "incident-analysis",
                "corr-123",
                "COMPLETED",
                CREATED_AT,
                COMPLETED_AT,
                COMPLETED_AT
        );
    }

    private AnalysisChatMessageResponse message(
            String id,
            String role,
            String status,
            String content,
            List<AnalysisEvidenceSection> toolEvidenceSections
    ) {
        return new AnalysisChatMessageResponse(
                id,
                role,
                status,
                content,
                null,
                null,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT,
                toolEvidenceSections,
                List.of(),
                List.of(),
                null
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

    private static final class CapturingChatProvider implements AnalysisAiChatProvider {

        private final RuntimeException exception;
        private AnalysisAiChatRequest request;

        private CapturingChatProvider() {
            this(null);
        }

        private CapturingChatProvider(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public AnalysisAiChatResponse chat(
                AnalysisAiChatRequest request,
                AnalysisAiToolEvidenceListener toolEvidenceListener
        ) {
            throw new UnsupportedOperationException("Use activity-aware chat in this test.");
        }

        @Override
        public AnalysisAiChatResponse chat(
                AnalysisAiChatRequest request,
                AnalysisAiToolEvidenceListener toolEvidenceListener,
                AnalysisAiActivityListener activityListener
        ) {
            this.request = request;
            if (exception != null) {
                throw exception;
            }

            toolEvidenceListener.onToolEvidenceUpdated(section("GitLab", "follow-up-file-chunk", "Code chunk"));
            activityListener.onAiActivity(new AnalysisAiActivityEvent(
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
                    Map.of("projectName", "catalog-service")
            ));
            return new AnalysisAiChatResponse(
                    "test-provider",
                    "Odpowiedz lokalna.",
                    "Follow-up prompt",
                    "follow-up-session-1"
            );
        }
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
