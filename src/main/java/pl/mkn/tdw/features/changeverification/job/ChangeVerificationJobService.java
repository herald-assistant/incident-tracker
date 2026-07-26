package pl.mkn.tdw.features.changeverification.job;

import org.springframework.stereotype.Service;
import org.springframework.core.task.TaskExecutor;
import lombok.extern.slf4j.Slf4j;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationAiResponse;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysis;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationComplianceAnalysisProvider;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationSmokePackAnalysis;
import pl.mkn.tdw.features.changeverification.ai.ChangeVerificationSmokePackAnalysisProvider;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationFindingSeverity;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobMode;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStateSnapshot;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationExecutionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeExecutionRequest;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokePackResponse;
import pl.mkn.tdw.features.changeverification.job.error.ChangeVerificationJobNotFoundException;
import pl.mkn.tdw.features.changeverification.job.state.ChangeVerificationJobState;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryListener;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryService;
import pl.mkn.tdw.features.changeverification.execution.ChangeVerificationSmokeExecutionService;
import pl.mkn.tdw.features.changeverification.smoke.ChangeVerificationPostmanCollectionRenderer;
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
    private final ChangeVerificationComplianceAnalysisProvider complianceAnalysisProvider;
    private final ChangeVerificationSmokePackAnalysisProvider smokePackAnalysisProvider;
    private final ChangeVerificationPostmanCollectionRenderer postmanCollectionRenderer;
    private final ChangeVerificationSmokeExecutionService smokeExecutionService;
    private final TaskExecutor applicationTaskExecutor;

    public ChangeVerificationJobService(
            ChangeVerificationSourceDiscoveryService sourceDiscoveryService,
            ChangeVerificationComplianceAnalysisProvider complianceAnalysisProvider,
            ChangeVerificationSmokePackAnalysisProvider smokePackAnalysisProvider,
            ChangeVerificationPostmanCollectionRenderer postmanCollectionRenderer,
            ChangeVerificationSmokeExecutionService smokeExecutionService,
            TaskExecutor applicationTaskExecutor
    ) {
        this.sourceDiscoveryService = sourceDiscoveryService;
        this.complianceAnalysisProvider = complianceAnalysisProvider;
        this.smokePackAnalysisProvider = smokePackAnalysisProvider;
        this.postmanCollectionRenderer = postmanCollectionRenderer;
        this.smokeExecutionService = smokeExecutionService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    public ChangeVerificationJobStateSnapshot startJob(ChangeVerificationJobStartRequest request) {
        var jobId = UUID.randomUUID().toString();
        var job = new ChangeVerificationJobState(jobId, request);
        jobs.put(jobId, job);
        job.markSourceDiscoveryStarted();
        applicationTaskExecutor.execute(() -> runJob(jobId, job, request));
        return job.snapshot();
    }

    private void runJob(String jobId, ChangeVerificationJobState job, ChangeVerificationJobStartRequest request) {
        try {
            var sourceDiscovery = sourceDiscoveryService.discover(request, sourceDiscoveryListener(job));
            job.markSourceDiscoveryCompleted(sourceDiscovery);

            var complianceAnalysis = (complianceRequested(request))
                    ? runCompliance(jobId, job, request, sourceDiscovery)
                    : null;
            var smokePackAnalysis = (smokePackRequested(request))
                    ? runSmokePack(jobId, job, request, sourceDiscovery, complianceAnalysis)
                    : null;

            job.markCompleted(complianceAnalysis, smokePackAnalysis);
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
        }
    }

    private ChangeVerificationSourceDiscoveryListener sourceDiscoveryListener(ChangeVerificationJobState job) {
        return new ChangeVerificationSourceDiscoveryListener() {
            @Override
            public void onJiraMaterialStarted(String issueKey) {
                job.markJiraMaterialStarted(issueKey);
            }

            @Override
            public void onJiraMaterialCompleted(
                    String issueKey,
                    pl.mkn.tdw.integrations.jira.JiraIssueMaterial jiraIssue,
                    List<String> limitations
            ) {
                job.markJiraMaterialCompleted(issueKey, jiraIssue, limitations);
            }

            @Override
            public void onMergeRequestDiscoveryStarted(String issueKey) {
                job.markMergeRequestDiscoveryStarted(issueKey);
            }

            @Override
            public void onMergeRequestDiscoveryCompleted(
                    String issueKey,
                    pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult mergeRequests,
                    List<String> limitations
            ) {
                job.markMergeRequestDiscoveryCompleted(issueKey, mergeRequests, limitations);
                job.markChangedFilesCompleted(issueKey);
            }

            @Override
            public void onInstructionContextStarted(
                    pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult mergeRequests
            ) {
                job.markInstructionContextStarted(mergeRequests);
            }

            @Override
            public void onInstructionContextCompleted(
                    pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestSearchResult mergeRequests,
                    pl.mkn.tdw.integrations.gitlab.instructions.InstructionContextResult instructionContext,
                    List<String> limitations
            ) {
                job.markInstructionContextCompleted(mergeRequests, instructionContext, limitations);
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
        var complianceAnalysis = analyzeCompliance(
                jobId,
                request,
                sourceDiscovery,
                toolEvidenceListener(job),
                activityListener(job)
        );
        job.markAiVerificationCompleted(complianceAnalysis);
        return complianceAnalysis;
    }

    private ChangeVerificationSmokePackAnalysis runSmokePack(
            String jobId,
            ChangeVerificationJobState job,
            ChangeVerificationJobStartRequest request,
            pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationComplianceAnalysis complianceAnalysis
    ) {
        job.markSmokePackGenerationStarted();
        var smokePackAnalysis = analyzeSmokePack(
                jobId,
                request,
                sourceDiscovery,
                complianceAnalysis,
                toolEvidenceListener(job),
                activityListener(job)
        );
        job.markSmokePackGenerationCompleted(smokePackAnalysis);
        return smokePackAnalysis;
    }

    public ChangeVerificationJobStateSnapshot getJob(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new ChangeVerificationJobNotFoundException(jobId);
        }
        return job.snapshot();
    }

    public ChangeVerificationSmokePackResponse getSmokePack(String jobId) {
        return job(jobId).smokePack();
    }

    public ChangeVerificationSmokePackResponse updateSmokePack(
            String jobId,
            ChangeVerificationSmokePackResponse smokePack
    ) {
        var job = job(jobId);
        job.updateSmokePack(smokePack);
        return job.smokePack();
    }

    public Map<String, Object> postmanCollection(String jobId) {
        return postmanCollectionRenderer.render(job(jobId).smokePack());
    }

    public ChangeVerificationExecutionResponse executeSmokePack(
            String jobId,
            ChangeVerificationSmokeExecutionRequest request
    ) {
        var job = job(jobId);
        var testResults = smokeExecutionService.execute(job.smokePack(), request);
        var response = executionResponse(testResults);
        job.markExecutionCompleted(response);
        return response;
    }

    private ChangeVerificationJobState job(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new ChangeVerificationJobNotFoundException(jobId);
        }
        return job;
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
                    null
            );
        }
    }

    private boolean complianceRequested(ChangeVerificationJobStartRequest request) {
        return request.modes().contains(ChangeVerificationJobMode.CHECK_COMPLIANCE);
    }

    private boolean smokePackRequested(ChangeVerificationJobStartRequest request) {
        return request.modes().contains(ChangeVerificationJobMode.GENERATE_SMOKE_PACK)
                || request.modes().contains(ChangeVerificationJobMode.EXECUTE_SMOKE_PACK);
    }

    private ChangeVerificationSmokePackAnalysis analyzeSmokePack(
            String jobId,
            ChangeVerificationJobStartRequest request,
            pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationComplianceAnalysis complianceAnalysis,
            AnalysisAiToolEvidenceListener toolEvidenceListener,
            AnalysisAiActivityListener activityListener
    ) {
        try {
            return smokePackAnalysisProvider.analyze(
                    jobId,
                    request,
                    sourceDiscovery,
                    complianceAnalysis,
                    toolEvidenceListener,
                    activityListener
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Change Verification AI smoke pack generation failed jobId={} issueKey={} reason={}",
                    jobId,
                    request.issueKey(),
                    exception.getMessage(),
                    exception
            );
            return new ChangeVerificationSmokePackAnalysis(
                    new ChangeVerificationSmokePackResponse(
                            true,
                            "INCONCLUSIVE",
                            null,
                            List.of(),
                            List.of("AI smoke pack generation failed: " + safeMessage(exception)),
                            List.of("Create smoke tests manually from Jira acceptance criteria and MR changed files."),
                            "low"
                    ),
                    null,
                    null,
                    null
            );
        }
    }

    private String safeMessage(RuntimeException exception) {
        return org.springframework.util.StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private AnalysisAiToolEvidenceListener toolEvidenceListener(ChangeVerificationJobState job) {
        return job::markAiToolEvidenceUpdated;
    }

    private AnalysisAiActivityListener activityListener(ChangeVerificationJobState job) {
        return job::markAiActivity;
    }

    private ChangeVerificationExecutionResponse executionResponse(
            List<pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeTestExecutionResponse> testResults
    ) {
        var executedIds = testResults.stream()
                .filter(result -> result.http() != null)
                .map(pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeTestExecutionResponse::testId)
                .toList();
        var cleanupActions = testResults.stream()
                .map(pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeTestExecutionResponse::cleanup)
                .filter(java.util.Objects::nonNull)
                .map(cleanup -> cleanup.strategy() + ": " + cleanup.status()
                        + (cleanup.action() != null ? " " + cleanup.action() : ""))
                .toList();
        var status = aggregateExecutionStatus(testResults);
        var limits = testResults.stream()
                .flatMap(result -> result.responseAssertions().stream())
                .filter(assertion -> "NEEDS_MANUAL_REVIEW".equals(assertion.status())
                        || "BLOCKED_BY_POLICY".equals(assertion.status()))
                .map(assertion -> assertion.type() + " " + assertion.target() + ": " + assertion.message())
                .distinct()
                .toList();
        return new ChangeVerificationExecutionResponse(
                true,
                status,
                executedIds,
                testResults,
                cleanupActions,
                null,
                limits
        );
    }

    private String aggregateExecutionStatus(
            List<pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeTestExecutionResponse> testResults
    ) {
        if (testResults.isEmpty()) {
            return "SKIPPED";
        }
        if (testResults.stream().anyMatch(result -> "FAILED".equals(result.status()))) {
            return "FAILED";
        }
        if (testResults.stream().anyMatch(result -> "PASSED_WITH_WARNINGS".equals(result.status())
                || "NEEDS_REVIEW".equals(result.status()))) {
            return "PASSED_WITH_WARNINGS";
        }
        return "PASSED";
    }
}
