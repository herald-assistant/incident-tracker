package pl.mkn.tdw.features.incidentanalysis.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatProvider;
import pl.mkn.tdw.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisDataNotFoundException;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisExecution;
import pl.mkn.tdw.features.incidentanalysis.flow.AnalysisOrchestrator;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisChatMessageRequest;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobStartRequest;
import pl.mkn.tdw.features.incidentanalysis.job.error.AnalysisJobNotFoundException;
import pl.mkn.tdw.features.incidentanalysis.job.localworkspace.IncidentAnalysisLocalRunPersistence;
import pl.mkn.tdw.features.incidentanalysis.job.state.AnalysisJobState;
import pl.mkn.tdw.features.incidentanalysis.job.state.AnalysisJobStateListener;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AnalysisJobService {

    private final AnalysisOrchestrator analysisOrchestrator;
    private final AnalysisAiChatProvider analysisAiChatProvider;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;
    private final IncidentAnalysisLocalRunPersistence localRunPersistence;

    private final Map<String, AnalysisJobState> jobs = new ConcurrentHashMap<>();

    public AnalysisJobService(
            AnalysisOrchestrator analysisOrchestrator,
            AnalysisAiChatProvider analysisAiChatProvider,
            TaskExecutor applicationTaskExecutor
    ) {
        this(
                analysisOrchestrator,
                analysisAiChatProvider,
                applicationTaskExecutor,
                () -> AnalysisAiAuthRef.localToken(null),
                new CopilotRunAuthMapper(),
                auth -> new pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessToken(
                        "test-token",
                        null,
                        null,
                        false
                ),
                IncidentAnalysisLocalRunPersistence.NO_OP
        );
    }

    public AnalysisJobService(
            AnalysisOrchestrator analysisOrchestrator,
            AnalysisAiChatProvider analysisAiChatProvider,
            TaskExecutor applicationTaskExecutor,
            AnalysisAiAuthRefResolver authRefResolver,
            CopilotRunAuthMapper runAuthMapper,
            CopilotAccessTokenResolver accessTokenResolver
    ) {
        this(
                analysisOrchestrator,
                analysisAiChatProvider,
                applicationTaskExecutor,
                authRefResolver,
                runAuthMapper,
                accessTokenResolver,
                IncidentAnalysisLocalRunPersistence.NO_OP
        );
    }

    public AnalysisJobStateSnapshot startAnalysis(AnalysisJobStartRequest request) {
        var authRef = authRefResolver.resolveForCurrentRequest();
        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));
        var analysisId = UUID.randomUUID().toString();
        var job = new AnalysisJobState(
                analysisId,
                request.correlationId(),
                request.aiOptions(),
                authRef,
                analysisOrchestrator.providerDescriptors()
        );

        jobs.put(analysisId, job);
        applicationTaskExecutor.execute(() -> runAnalysis(job, request, authRef));

        return job.snapshot();
    }

    public AnalysisJobStateSnapshot getAnalysis(String analysisId) {
        return jobOrThrow(analysisId).snapshot();
    }

    public AnalysisJobStateSnapshot startChatMessage(String analysisId, AnalysisChatMessageRequest request) {
        var job = jobOrThrow(analysisId);
        accessTokenResolver.resolve(runAuthMapper.toRunAuth(job.completedAuthRefForChat()));
        var userMessageId = UUID.randomUUID().toString();
        var assistantMessageId = UUID.randomUUID().toString();
        var chatRequest = job.startChatMessage(userMessageId, assistantMessageId, request.message());

        applicationTaskExecutor.execute(() -> runChat(job, assistantMessageId, chatRequest));

        return job.snapshot();
    }

    private void runAnalysis(AnalysisJobState job, AnalysisJobStartRequest request, AnalysisAiAuthRef authRef) {
        try {
            var execution = analysisOrchestrator.analyze(
                    request.correlationId(),
                    request.aiOptions(),
                    authRef,
                    new AnalysisJobStateListener(job)
            );
            job.markCompleted(execution);
            persistCompletedInitialRun(job, execution);
        } catch (AnalysisDataNotFoundException exception) {
            job.markNotFound("ANALYSIS_DATA_NOT_FOUND", exception.getMessage());
        } catch (RuntimeException exception) {
            log.error(
                    "Analysis job failed correlationId={} errorCode=ANALYSIS_FAILED message={}",
                    request.correlationId(),
                    exception.getMessage(),
                    exception
            );
            job.markFailed(
                    "ANALYSIS_FAILED",
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Unexpected analysis failure."
            );
        }
    }

    private void persistCompletedInitialRun(AnalysisJobState job, AnalysisExecution execution) {
        var snapshot = job.snapshot();
        try {
            localRunPersistence.persistCompletedInitialRun(
                    snapshot,
                    execution.aiRequest(),
                    execution.aiResponse() != null ? execution.aiResponse().copilotSessionId() : null
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to persist completed local analysis run analysisId={} correlationId={} reason={}",
                    snapshot.analysisId(),
                    snapshot.correlationId(),
                    exception.getMessage()
            );
        }
    }

    private void runChat(
            AnalysisJobState job,
            String assistantMessageId,
            AnalysisAiChatRequest request
    ) {
        try {
            var response = analysisAiChatProvider.chat(
                    request,
                    section -> job.markChatToolEvidenceUpdated(assistantMessageId, section),
                    event -> job.markChatAiActivity(assistantMessageId, event)
            );
            job.markChatCompleted(
                    assistantMessageId,
                    response.content(),
                    response.prompt(),
                    response.copilotSessionId()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Analysis chat failed correlationId={} errorCode=ANALYSIS_CHAT_FAILED message={}",
                    request.correlationId(),
                    exception.getMessage(),
                    exception
            );
            job.markChatFailed(
                    assistantMessageId,
                    "ANALYSIS_CHAT_FAILED",
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Unexpected follow-up chat failure."
            );
        }
    }

    private AnalysisJobState jobOrThrow(String analysisId) {
        var job = jobs.get(analysisId);
        if (job == null) {
            throw new AnalysisJobNotFoundException(analysisId);
        }
        return job;
    }
}
