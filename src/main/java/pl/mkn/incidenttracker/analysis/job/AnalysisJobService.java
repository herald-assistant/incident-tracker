package pl.mkn.incidenttracker.analysis.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.analysis.flow.AnalysisDataNotFoundException;
import pl.mkn.incidenttracker.analysis.flow.AnalysisExecutionListener;
import pl.mkn.incidenttracker.analysis.flow.AnalysisOrchestrator;
import pl.mkn.incidenttracker.analysis.flow.AnalysisRequest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisJobService {

    private final AnalysisOrchestrator analysisOrchestrator;
    @Qualifier("applicationTaskExecutor")
    private final TaskExecutor taskExecutor;

    private final Map<String, AnalysisJobState> jobs = new ConcurrentHashMap<>();

    public AnalysisJobResponse startAnalysis(AnalysisRequest request) {
        return startAnalysis(AnalysisJobStartRequest.from(request));
    }

    public AnalysisJobResponse startAnalysis(AnalysisJobStartRequest request) {
        var analysisId = UUID.randomUUID().toString();
        var job = new AnalysisJobState(
                analysisId,
                request.correlationId(),
                request.aiOptions(),
                analysisOrchestrator.providerDescriptors()
        );

        jobs.put(analysisId, job);
        taskExecutor.execute(() -> runAnalysis(job, request));

        return job.snapshot();
    }

    public AnalysisJobResponse getAnalysis(String analysisId) {
        return jobs.computeIfAbsent(analysisId, missingId -> {
            throw new AnalysisJobNotFoundException(missingId);
        }).snapshot();
    }

    private void runAnalysis(AnalysisJobState job, AnalysisJobStartRequest request) {
        try {
            var execution = analysisOrchestrator.analyze(
                    request.correlationId(),
                    request.aiOptions(),
                    new JobProgressListener(job)
            );
            job.markCompleted(execution.result());
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

    private static final class JobProgressListener implements AnalysisExecutionListener {

        private final AnalysisJobState job;

        private JobProgressListener(AnalysisJobState job) {
            this.job = job;
        }

        @Override
        public void onProviderStarted(AnalysisEvidenceProvider provider, AnalysisContext context) {
            job.markEvidenceStepStarted(provider.descriptor());
        }

        @Override
        public void onProviderCompleted(
                AnalysisEvidenceProvider provider,
                pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection section,
                AnalysisContext updatedContext
        ) {
            job.markEvidenceStepCompleted(provider.descriptor(), section);
        }

        @Override
        public void onAiStarted(
                pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest request,
                AnalysisContext context
        ) {
            job.markAiStarted();
        }

        @Override
        public void onAiPromptPrepared(
                pl.mkn.incidenttracker.analysis.ai.AnalysisAiAnalysisRequest request,
                String preparedPrompt,
                AnalysisContext context
        ) {
            job.markAiPromptPrepared(preparedPrompt);
        }

        @Override
        public void onAiToolEvidenceUpdated(pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection section) {
            job.markAiToolEvidenceUpdated(section);
        }
    }

}
