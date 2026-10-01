package pl.mkn.tdw.features.deliverycomplexityassessment.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAccessTokenResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryComplexityAssessmentProperties;
import pl.mkn.tdw.features.deliverycomplexityassessment.ai.DeliveryAssessmentScoringService;
import pl.mkn.tdw.features.deliverycomplexityassessment.ai.DeliveryPromptPreparationService;
import pl.mkn.tdw.features.deliverycomplexityassessment.ai.DeliveryUnitAssessmentProvider;
import pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit.DeliveryUnitBuilder;
import pl.mkn.tdw.features.deliverycomplexityassessment.evidence.DeliveryEvidencePacketBuilder;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryComplexityAssessmentJobStartRequest;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryComplexityAssessmentJobStateSnapshot;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.error.DeliveryAssessmentJobNotFoundException;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.error.DeliveryAssessmentStartException;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.localworkspace.DeliveryAssessmentLocalRunPersistence;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.state.DeliveryComplexityAssessmentJobState;
import pl.mkn.tdw.features.deliverycomplexityassessment.source.DeliveryAssessmentSourceDiscoveryService;
import pl.mkn.tdw.features.deliverycomplexityassessment.source.DeliveryAssessmentSourceListener;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeliveryComplexityAssessmentJobService {

    private final Map<String, DeliveryComplexityAssessmentJobState> jobs = new ConcurrentHashMap<>();
    private final DeliveryAssessmentSourceDiscoveryService sourceDiscoveryService;
    private final DeliveryUnitBuilder deliveryUnitBuilder;
    private final DeliveryEvidencePacketBuilder evidencePacketBuilder;
    private final DeliveryPromptPreparationService promptPreparationService;
    private final DeliveryUnitAssessmentProvider assessmentProvider;
    private final DeliveryAssessmentScoringService scoringService;
    private final DeliveryAssessmentLocalRunPersistence localRunPersistence;
    private final DeliveryAssessmentUnitExecutor unitExecutor;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final CopilotRunAuthMapper runAuthMapper;
    private final CopilotAccessTokenResolver accessTokenResolver;
    private final LocalWorkspaceProperties workspaceProperties;
    private final DeliveryComplexityAssessmentProperties properties;

    public DeliveryComplexityAssessmentJobStateSnapshot startJob(
            DeliveryComplexityAssessmentJobStartRequest request
    ) {
        validateStart(request);
        var authRef = resolveAiAuth();
        var jobId = UUID.randomUUID().toString();
        var job = new DeliveryComplexityAssessmentJobState(jobId, request);
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

    public DeliveryComplexityAssessmentJobStateSnapshot getJob(String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new DeliveryAssessmentJobNotFoundException(jobId);
        }
        return job.snapshot();
    }

    private void runJob(
            DeliveryComplexityAssessmentJobState job,
            DeliveryComplexityAssessmentJobStartRequest request,
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
            log.error("Delivery Complexity Assessment failed jobId={} reason={}",
                    job.snapshot().jobId(), exception.getMessage(), exception);
            job.markFailed("DELIVERY_ASSESSMENT_FAILED", safeMessage(exception));
            persistSnapshot(job, false);
        }
    }

    private CompletableFuture<Void> runUnit(
            DeliveryComplexityAssessmentJobState job,
            DeliveryComplexityAssessmentJobStartRequest request,
            AnalysisAiAuthRef authRef,
            DeliveryUnit unit
    ) {
        return unitExecutor.runAsync(() -> assessUnit(job, request, authRef, unit), properties.getItemTimeout())
                .exceptionally(failure -> {
                    job.markUnitFailed(unit.unitId(), "DELIVERY_UNIT_EXECUTION_FAILED", safeMessage(failure));
                    persistSnapshot(job, false);
                    return null;
                });
    }

    private void assessUnit(
            DeliveryComplexityAssessmentJobState job,
            DeliveryComplexityAssessmentJobStartRequest request,
            AnalysisAiAuthRef authRef,
            DeliveryUnit unit
    ) {
        job.markUnitCollecting(unit.unitId());
        persistSnapshot(job, false);
        var packet = evidencePacketBuilder.build(unit);
        job.markUnitVisibilityLimits(unit.unitId(), packet.visibilityLimits());
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

        var preparation = promptPreparationService.prepare(packet);
        job.markUnitPreparedPrompt(unit.unitId(), preparation.prompt());
        persistSnapshot(job, false);
        job.markUnitAnalyzing(unit.unitId());
        persistSnapshot(job, false);
        var analysis = assessmentProvider.analyze(
                job.snapshot().jobId() + ":" + unit.unitId(),
                request.aiOptions(),
                authRef,
                packet,
                preparation,
                event -> {
                    job.markAiActivity(unit.unitId(), event);
                    persistSnapshot(job, false);
                },
                rawResponse -> {
                    job.markUnitRawAiResponse(unit.unitId(), rawResponse);
                    persistSnapshot(job, false);
                }
        );
        var classification = analysis.response().classification();
        if ("INSUFFICIENT_EVIDENCE".equals(classification)) {
            var limitations = analysis.response().visibilityLimits().isEmpty()
                    ? List.of("AI could not establish a grounded assessment from the available evidence.")
                    : analysis.response().visibilityLimits();
            job.markUnitNotScorable(unit.unitId(), limitations, analysis.usage());
        } else if ("EXCLUDED".equals(classification)) {
            var limitations = new ArrayList<>(analysis.response().visibilityLimits());
            limitations.add("AI classified the observable delivery as semantically excluded.");
            job.markUnitExcluded(unit.unitId(), limitations, analysis.usage());
        } else {
            job.markUnitCompleted(
                    unit.unitId(),
                    scoringService.score(analysis.response()),
                    analysis.usage()
            );
        }
        persistSnapshot(job, false);
    }

    private DeliveryAssessmentSourceListener sourceListener(DeliveryComplexityAssessmentJobState job) {
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

    private void validateStart(DeliveryComplexityAssessmentJobStartRequest request) {
        if (!properties.isEnabled()) {
            throw new DeliveryAssessmentStartException(
                    "DELIVERY_ASSESSMENT_DISABLED",
                    "Delivery Complexity Assessment is disabled.",
                    UserFacingErrorType.SERVICE_UNAVAILABLE
            );
        }
        if (!workspaceProperties.isEnabled()) {
            throw new DeliveryAssessmentStartException(
                    "DELIVERY_ASSESSMENT_HISTORY_DISABLED",
                    "Delivery Complexity Assessment requires local Analysis History storage.",
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

    private void persistSnapshot(DeliveryComplexityAssessmentJobState job, boolean required) {
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
