package pl.mkn.incidenttracker.features.incidentanalysis.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatProvider;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.flow.AnalysisDataNotFoundException;
import pl.mkn.incidenttracker.features.incidentanalysis.flow.AnalysisOrchestrator;
import pl.mkn.incidenttracker.features.incidentanalysis.job.api.AnalysisChatMessageRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.job.api.AnalysisJobStateSnapshot;
import pl.mkn.incidenttracker.features.incidentanalysis.job.api.AnalysisJobStartRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.job.error.AnalysisJobNotFoundException;
import pl.mkn.incidenttracker.features.incidentanalysis.job.state.AnalysisJobState;
import pl.mkn.incidenttracker.features.incidentanalysis.job.state.AnalysisJobStateListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisJobService {

    private final AnalysisOrchestrator analysisOrchestrator;
    private final AnalysisAiChatProvider analysisAiChatProvider;
    private final TaskExecutor applicationTaskExecutor;

    private final Map<String, AnalysisJobState> jobs = new ConcurrentHashMap<>();

    public AnalysisJobStateSnapshot startAnalysis(AnalysisJobStartRequest request) {
        var analysisId = UUID.randomUUID().toString();
        var job = new AnalysisJobState(
                analysisId,
                request.correlationId(),
                request.aiOptions(),
                analysisOrchestrator.providerDescriptors()
        );

        jobs.put(analysisId, job);
        applicationTaskExecutor.execute(() -> runAnalysis(job, request));

        return job.snapshot();
    }

    public AnalysisJobStateSnapshot getAnalysis(String analysisId) {
        return jobOrThrow(analysisId).snapshot();
    }

    public AnalysisJobStateSnapshot startChatMessage(String analysisId, AnalysisChatMessageRequest request) {
        var job = jobOrThrow(analysisId);
        var userMessageId = UUID.randomUUID().toString();
        var assistantMessageId = UUID.randomUUID().toString();
        var chatRequest = job.startChatMessage(userMessageId, assistantMessageId, request.message());

        applicationTaskExecutor.execute(() -> runChat(job, assistantMessageId, chatRequest));

        return job.snapshot();
    }

    private void runAnalysis(AnalysisJobState job, AnalysisJobStartRequest request) {
        try {
            var execution = analysisOrchestrator.analyze(
                    request.correlationId(),
                    request.aiOptions(),
                    new AnalysisJobStateListener(job)
            );
            job.markCompleted(execution);
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

    private void runChat(
            AnalysisJobState job,
            String assistantMessageId,
            AnalysisAiChatRequest request
    ) {
        try {
            var response = analysisAiChatProvider.chat(
                    request,
                    section -> job.markChatToolEvidenceUpdated(assistantMessageId, section)
            );
            job.markChatCompleted(assistantMessageId, response.content(), response.prompt());
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
