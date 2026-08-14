package pl.mkn.tdw.features.deliveryeffectivenessassessment.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.DeliveryEffectivenessAssessmentProperties;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAssessmentScoringService;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryUnitAssessmentProvider;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnitBuilder;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacketBuilder;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStartRequest;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStateSnapshot;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.error.DeliveryAssessmentJobNotFoundException;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.error.DeliveryAssessmentStartException;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.localworkspace.DeliveryAssessmentLocalRunPersistence;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.state.DeliveryEffectivenessAssessmentJobState;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentSourceDiscoveryService;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentSourceListener;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeliveryEffectivenessAssessmentJobService {

    private final Map<String, DeliveryEffectivenessAssessmentJobState> jobs = new ConcurrentHashMap<>();
    private final DeliveryAssessmentSourceDiscoveryService sourceDiscoveryService;
    private final DeliveryUnitBuilder deliveryUnitBuilder;
    private final DeliveryEvidencePacketBuilder evidencePacketBuilder;
    private final DeliveryUnitAssessmentProvider assessmentProvider;
    private final DeliveryAssessmentScoringService scoringService;
    private final DeliveryAssessmentLocalRunPersistence localRunPersistence;
    private final DeliveryAssessmentUnitExecutor unitExecutor;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;
    private final LocalWorkspaceProperties workspaceProperties;
    private final DeliveryEffectivenessAssessmentProperties properties;

    public DeliveryEffectivenessAssessmentJobStateSnapshot startJob(
            DeliveryEffectivenessAssessmentJobStartRequest request
    ) {
        validateStart(request);
        var authRef = resolveAiAuth();
        var jobId = UUID.randomUUID().toString();
        var job = new DeliveryEffectivenessAssessmentJobState(jobId, request);
        jobs.put(jobId, job);
        try {
            persistSnapshot(job, true);
        } catch (RuntimeException exception) {
            jobs.remove(jobId);
            throw new DeliveryAssessmentStartException(
                    "DELIVERY_ASSESSMENT_RUN_STORAGE_UNAVAILABLE",
                    "The assessment could not start because its initial Analysis History snapshot could not be saved.",
                    UserFacingErrorType.SERVICE_UNAVAILABLE
            );
        }

        try {
            applicationTaskExecutor.execute(() -> runJob(job, request, authRef));
        } catch (RuntimeException exception) {
            job.markFailed("DELIVERY_ASSESSMENT_EXECUTOR_UNAVAILABLE", "Assessment execution could not be scheduled.");
            persistSnapshot(job, false);
        }
        return job.snapshot();
    }

    public DeliveryEffectivenessAssessmentJobStateSnapshot getJob(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new DeliveryAssessmentJobNotFoundException(jobId);
        }
        return job.snapshot();
    }

    private void runJob(
            DeliveryEffectivenessAssessmentJobState job,
            DeliveryEffectivenessAssessmentJobStartRequest request,
            AnalysisAiAuthRef authRef
    ) {
        try {
            job.markDiscoveryStarted();
            persistSnapshot(job, false);
            var source = sourceDiscoveryService.discover(request, sourceListener(job));
            var units = deliveryUnitBuilder.build(source.issues());
            job.markUnitsReady(source, units);
            persistSnapshot(job, false);
            if (units.isEmpty()) {
                return;
            }

            var futures = units.stream()
                    .map(unit -> runUnit(job, request, authRef, unit))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
            job.finalizeJob();
            persistSnapshot(job, false);
        } catch (RuntimeException exception) {
            log.error("Delivery Effectiveness Assessment failed jobId={} reason={}",
                    job.snapshot().jobId(), exception.getMessage(), exception);
            job.markFailed("DELIVERY_ASSESSMENT_FAILED", safeMessage(exception));
            persistSnapshot(job, false);
        }
    }

    private CompletableFuture<Void> runUnit(
            DeliveryEffectivenessAssessmentJobState job,
            DeliveryEffectivenessAssessmentJobStartRequest request,
            AnalysisAiAuthRef authRef,
            DeliveryUnit unit
    ) {
        return unitExecutor.runAsync(() -> assessUnit(job, request, authRef, unit))
                .orTimeout(Math.max(1, properties.getItemTimeout().toMillis()), TimeUnit.MILLISECONDS)
                .exceptionally(failure -> {
                    job.markUnitFailed(unit.unitId(), "DELIVERY_UNIT_EXECUTION_FAILED", safeMessage(failure));
                    persistSnapshot(job, false);
                    return null;
                });
    }

    private void assessUnit(
            DeliveryEffectivenessAssessmentJobState job,
            DeliveryEffectivenessAssessmentJobStartRequest request,
            AnalysisAiAuthRef authRef,
            DeliveryUnit unit
    ) {
        job.markUnitCollecting(unit.unitId());
        persistSnapshot(job, false);
        var packet = evidencePacketBuilder.build(unit);
        if (!packet.scorable()) {
            job.markUnitNotScorable(unit.unitId(), "No merged code evidence with changed files was available.");
            persistSnapshot(job, false);
            return;
        }
        if (packet.mechanicallyExcluded()) {
            job.markUnitExcluded(unit.unitId(), "All observed changed paths were generated or mechanical artifacts.");
            persistSnapshot(job, false);
            return;
        }

        job.markUnitAnalyzing(unit.unitId());
        persistSnapshot(job, false);
        var analysis = assessmentProvider.analyze(
                job.snapshot().jobId() + ":" + unit.unitId(),
                request.aiOptions(),
                authRef,
                packet,
                event -> {
                    job.markAiActivity(unit.unitId(), event);
                    persistSnapshot(job, false);
                }
        );
        var classification = analysis.response().classification();
        if ("INSUFFICIENT_EVIDENCE".equals(classification)) {
            job.markUnitNotScorable(unit.unitId(), String.join("; ", analysis.response().visibilityLimits()));
        } else if ("EXCLUDED".equals(classification)) {
            job.markUnitExcluded(unit.unitId(), "AI classified the observable delivery as semantically excluded.");
        } else {
            job.markUnitCompleted(
                    unit.unitId(),
                    scoringService.score(analysis.response()),
                    analysis.usage(),
                    analysis.report()
            );
        }
        persistSnapshot(job, false);
    }

    private DeliveryAssessmentSourceListener sourceListener(DeliveryEffectivenessAssessmentJobState job) {
        return new DeliveryAssessmentSourceListener() {
            @Override
            public void onSearchCompleted(int discovered, int total, String effectiveJql) {
                job.markSearchCompleted(discovered, total, effectiveJql);
                persistSnapshot(job, false);
            }

            @Override
            public void onIssueProcessed(int completed, int total, String issueKey) {
                job.markIssueProcessed(completed, total);
                persistSnapshot(job, false);
            }
        };
    }

    private AnalysisAiAuthRef resolveAiAuth() {
        var authRef = authRefResolver.resolveForCurrentRequest();
        accessTokenResolver.resolve(runAuthMapper.toRunAuth(authRef));
        return authRef;
    }

    private void validateStart(DeliveryEffectivenessAssessmentJobStartRequest request) {
        if (!properties.isEnabled()) {
            throw new DeliveryAssessmentStartException(
                    "DELIVERY_ASSESSMENT_DISABLED",
                    "Delivery Effectiveness Assessment is disabled.",
                    UserFacingErrorType.SERVICE_UNAVAILABLE
            );
        }
        if (!workspaceProperties.isEnabled()) {
            throw new DeliveryAssessmentStartException(
                    "DELIVERY_ASSESSMENT_HISTORY_DISABLED",
                    "Delivery Effectiveness Assessment requires local Analysis History storage.",
                    UserFacingErrorType.SERVICE_UNAVAILABLE
            );
        }
        if (request.fromDate() != null && request.toDate() != null) {
            var rangeDays = ChronoUnit.DAYS.between(request.fromDate(), request.toDate()) + 1;
            if (rangeDays > properties.getMaxRangeDays()) {
                throw new DeliveryAssessmentStartException(
                        "DELIVERY_ASSESSMENT_RANGE_TOO_LARGE",
                        "Date range exceeds the configured maximum of " + properties.getMaxRangeDays() + " days.",
                        UserFacingErrorType.BAD_REQUEST
                );
            }
        }
    }

    private void persistSnapshot(DeliveryEffectivenessAssessmentJobState job, boolean required) {
        synchronized (job) {
            try {
                localRunPersistence.persistRunSnapshot(job.snapshot());
            } catch (RuntimeException exception) {
                if (required) {
                    throw exception;
                }
                log.warn("Delivery assessment snapshot persistence failed jobId={} reason={}",
                        job.snapshot().jobId(), exception.getMessage(), exception);
            }
        }
    }

    private String safeMessage(Throwable exception) {
        var current = exception;
        while (current.getCause() != null && current != current.getCause()) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage())
                ? current.getMessage()
                : current.getClass().getSimpleName();
    }
}
