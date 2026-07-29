package pl.mkn.tdw.features.changeverification.job;

import org.springframework.stereotype.Service;
import org.springframework.core.task.TaskExecutor;
import lombok.extern.slf4j.Slf4j;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationAiResponse;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysis;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysisProvider;
import pl.mkn.tdw.features.changeverification.ai.preparation.ChangeVerificationPromptPreparationService;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingSeverity;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStateSnapshot;
import pl.mkn.tdw.features.changeverification.job.error.ChangeVerificationJobNotFoundException;
import pl.mkn.tdw.features.changeverification.job.localworkspace.ChangeVerificationLocalRunPersistence;
import pl.mkn.tdw.features.changeverification.job.state.ChangeVerificationJobState;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryListener;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryService;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityListener;
import pl.mkn.tdw.shared.evidence.AnalysisAiToolEvidenceListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

@Service
@Slf4j
public class ChangeVerificationJobService {

    private final Map<String, ChangeVerificationJobState> jobs = new ConcurrentHashMap<>();
    private final ChangeVerificationSourceDiscoveryService sourceDiscoveryService;
    private final ChangeVerificationPromptPreparationService compliancePromptPreparationService;
    private final ChangeVerificationComplianceAnalysisProvider complianceAnalysisProvider;
    private final ChangeVerificationLocalRunPersistence localRunPersistence;
    private final TaskExecutor applicationTaskExecutor;

    public ChangeVerificationJobService(
            ChangeVerificationSourceDiscoveryService sourceDiscoveryService,
            ChangeVerificationPromptPreparationService compliancePromptPreparationService,
            ChangeVerificationComplianceAnalysisProvider complianceAnalysisProvider,
            ChangeVerificationLocalRunPersistence localRunPersistence,
            TaskExecutor applicationTaskExecutor
    ) {
        this.sourceDiscoveryService = sourceDiscoveryService;
        this.compliancePromptPreparationService = compliancePromptPreparationService;
        this.complianceAnalysisProvider = complianceAnalysisProvider;
        this.localRunPersistence = localRunPersistence != null
                ? localRunPersistence
                : ChangeVerificationLocalRunPersistence.NO_OP;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    public ChangeVerificationJobStateSnapshot startJob(ChangeVerificationJobStartRequest request) {
        var jobId = UUID.randomUUID().toString();
        var job = new ChangeVerificationJobState(jobId, request);
        jobs.put(jobId, job);
        job.markSourceDiscoveryStarted();
        persistRunSnapshot(job);
        applicationTaskExecutor.execute(() -> runJob(jobId, job, request));
        return job.snapshot();
    }

    private void runJob(String jobId, ChangeVerificationJobState job, ChangeVerificationJobStartRequest request) {
        try {
            var sourceDiscovery = sourceDiscoveryService.discover(request, sourceDiscoveryListener(job));
            job.markSourceDiscoveryCompleted(sourceDiscovery);
            persistRunSnapshot(job);
            job.markPreparedPrompt(initialPreparedPrompt(request, sourceDiscovery));
            persistRunSnapshot(job);

            var complianceAnalysis = runCompliance(jobId, job, request, sourceDiscovery);
            job.markCompleted(complianceAnalysis);
            persistRunSnapshot(job);
        } catch (RuntimeException exception) {
            log.error(
                    "Change Verification job failed jobId={} issueKey={} reason={}",
                    jobId,
                    request.issueKey(),
                    exception.getMessage(),
                    exception
            );
            job.markFailed(
                    "CHANGE_VERIFICATION_FAILED",
                    org.springframework.util.StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Unexpected Change Verification failure."
            );
            persistRunSnapshot(job);
        }
    }

    private ChangeVerificationSourceDiscoveryListener sourceDiscoveryListener(ChangeVerificationJobState job) {
        return new ChangeVerificationSourceDiscoveryListener() {
            @Override
            public void onJiraMaterialStarted(String issueKey) {
                job.markJiraMaterialStarted(issueKey);
                persistRunSnapshot(job);
            }

            @Override
            public void onJiraMaterialCompleted(
                    String issueKey,
                    pl.mkn.tdw.integrations.jira.JiraIssueMaterial jiraIssue,
                    List<String> limitations
            ) {
                job.markJiraMaterialCompleted(issueKey, jiraIssue, limitations);
                persistRunSnapshot(job);
            }

            @Override
            public void onMergeRequestDiscoveryStarted(String issueKey) {
                job.markMergeRequestDiscoveryStarted(issueKey);
                persistRunSnapshot(job);
            }

            @Override
            public void onMergeRequestDiscoveryCompleted(
                    String issueKey,
                    pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult mergeRequests,
                    List<String> limitations
            ) {
                job.markMergeRequestDiscoveryCompleted(issueKey, mergeRequests, limitations);
                job.markChangedFilesCompleted(issueKey);
                persistRunSnapshot(job);
            }

            @Override
            public void onInstructionContextStarted(
                    pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult mergeRequests
            ) {
                job.markInstructionContextStarted(mergeRequests);
                persistRunSnapshot(job);
            }

            @Override
            public void onInstructionContextCompleted(
                    pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult mergeRequests,
                    pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextResult instructionContext,
                    List<String> limitations
            ) {
                job.markInstructionContextCompleted(mergeRequests, instructionContext, limitations);
                persistRunSnapshot(job);
            }
        };
    }

    private ChangeVerificationComplianceAnalysis runCompliance(
            String jobId,
            ChangeVerificationJobState job,
            ChangeVerificationJobStartRequest request,
            pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult sourceDiscovery
    ) {
        job.markAiVerificationStarted();
        persistRunSnapshot(job);
        var complianceAnalysis = analyzeCompliance(
                jobId,
                request,
                sourceDiscovery,
                toolEvidenceListener(job),
                activityListener(job)
        );
        job.markAiVerificationCompleted(complianceAnalysis);
        persistRunSnapshot(job);
        return complianceAnalysis;
    }

    public ChangeVerificationJobStateSnapshot getJob(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new ChangeVerificationJobNotFoundException(jobId);
        }
        return job.snapshot();
    }

    private ChangeVerificationComplianceAnalysis analyzeCompliance(
            String jobId,
            ChangeVerificationJobStartRequest request,
            pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        try {
            return complianceAnalysisProvider.analyze(
                    jobId,
                    request,
                    sourceDiscovery,
                    toolEvidenceListener,
                    activityListener
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Change Verification AI compliance check failed jobId={} issueKey={} reason={}",
                    jobId,
                    request.issueKey(),
                    exception.getMessage(),
                    exception
            );
            return new ChangeVerificationComplianceAnalysis(
                    new ChangeVerificationAiResponse(
                            "INCONCLUSIVE",
                            List.of(),
                            List.of(new ChangeVerificationFindingResponse(
                                    "cv-ai-unavailable",
                                    ChangeVerificationFindingSeverity.MEDIUM,
                                    "VISIBILITY",
                                    "AI compliance check was not completed.",
                                    "Copilot compliance analysis failed before returning a usable result.",
                                    List.of("change-verification/source-discovery"),
                                    "Inspect collected evidence manually or retry the verification run."
                            )),
                            List.of("Retry AI compliance verification after checking Copilot runtime availability."),
                            List.of("AI compliance check failed: " + safeMessage(exception)),
                            "low"
                    ),
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    private String initialPreparedPrompt(
            ChangeVerificationJobStartRequest request,
            pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult sourceDiscovery
    ) {
        return compliancePromptPreparationService.prepare(request, sourceDiscovery).prompt();
    }

    private String safeMessage(RuntimeException exception) {
        return org.springframework.util.StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private AnalysisAiToolEvidenceListener toolEvidenceListener(ChangeVerificationJobState job) {
        return section -> {
            job.markAiToolEvidenceUpdated(section);
            persistRunSnapshot(job);
        };
    }

    private AnalysisAiActivityListener activityListener(ChangeVerificationJobState job) {
        return event -> {
            job.markAiActivity(event);
            persistRunSnapshot(job);
        };
    }

    private void persistRunSnapshot(ChangeVerificationJobState job) {
        try {
            localRunPersistence.persistRunSnapshot(job.snapshot());
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to persist Change Verification local run snapshot jobId={} reason={}",
                    job.snapshot().jobId(),
                    exception.getMessage(),
                    exception
            );
        }
    }

}
