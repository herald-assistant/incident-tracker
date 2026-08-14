package pl.mkn.tdw.features.deliveryeffectivenessassessment.job.state;

import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAssessmentScore;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryAssessmentAggregateResponse;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryAssessmentUnitResponse;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStartRequest;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStateSnapshot;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.source.DeliveryAssessmentSourceResult;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import pl.mkn.tdw.shared.ai.AnalysisJobStepResponse;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class DeliveryEffectivenessAssessmentJobState {

    private final String jobId;
    private final DeliveryEffectivenessAssessmentJobStartRequest request;
    private final Instant createdAt = Instant.now();
    private Instant updatedAt = createdAt;
    private Instant completedAt;
    private String status = "QUEUED";
    private String currentStepCode = "QUEUED";
    private String currentStepLabel = "Queued";
    private String errorCode;
    private String errorMessage;
    private int discoveredIssues;
    private int processedIssues;
    private int totalIssues;
    private String effectiveJql;
    private final List<String> visibilityLimits = new ArrayList<>();
    private final List<AnalysisAiActivityEvent> aiActivityEvents = new ArrayList<>();
    private final LinkedHashMap<String, DeliveryAssessmentUnitState> units = new LinkedHashMap<>();
    private Instant discoveryStartedAt;
    private Instant discoveryCompletedAt;
    private Instant analysisStartedAt;

    public DeliveryEffectivenessAssessmentJobState(
            String jobId,
            DeliveryEffectivenessAssessmentJobStartRequest request
    ) {
        this.jobId = jobId;
        this.request = request;
    }

    public synchronized void markDiscoveryStarted() {
        status = "DISCOVERING";
        currentStepCode = "JIRA_DISCOVERY";
        currentStepLabel = "Jira discovery";
        discoveryStartedAt = Instant.now();
        touch();
    }

    public synchronized void markSearchCompleted(int discovered, int total, String jql) {
        discoveredIssues = discovered;
        totalIssues = total;
        effectiveJql = jql;
        touch();
    }

    public synchronized void markIssueProcessed(int completed, int total) {
        processedIssues = completed;
        discoveredIssues = total;
        touch();
    }

    public synchronized void markUnitsReady(
            DeliveryAssessmentSourceResult source,
            List<DeliveryUnit> deliveryUnits
    ) {
        effectiveJql = source.effectiveJql();
        totalIssues = source.jiraTotal();
        discoveredIssues = source.issues().size();
        processedIssues = source.issues().size();
        visibilityLimits.addAll(source.limitations());
        units.clear();
        deliveryUnits.forEach(unit -> units.put(unit.unitId(), new DeliveryAssessmentUnitState(unit)));
        discoveryCompletedAt = Instant.now();
        analysisStartedAt = discoveryCompletedAt;
        status = deliveryUnits.isEmpty() ? "COMPLETED_WITH_WARNINGS" : "ANALYZING";
        currentStepCode = deliveryUnits.isEmpty() ? "COMPLETED" : "UNIT_ASSESSMENT";
        currentStepLabel = deliveryUnits.isEmpty() ? "Completed with no assessable units" : "Delivery Unit assessment";
        if (deliveryUnits.isEmpty()) {
            completedAt = discoveryCompletedAt;
        }
        touch();
    }

    public synchronized void markUnitCollecting(String unitId) {
        unit(unitId).collecting();
        touch();
    }

    public synchronized void markUnitAnalyzing(String unitId) {
        unit(unitId).analyzing();
        touch();
    }

    public synchronized void markUnitCompleted(
            String unitId,
            DeliveryAssessmentScore score,
            AnalysisAiUsage usage,
            AnalysisReport report
    ) {
        unit(unitId).completed(score, usage, report);
        touch();
    }

    public synchronized void markUnitExcluded(String unitId, String limitation) {
        unit(unitId).excluded(limitation);
        touch();
    }

    public synchronized void markUnitNotScorable(String unitId, String limitation) {
        unit(unitId).notScorable(limitation);
        touch();
    }

    public synchronized void markUnitFailed(String unitId, String code, String message) {
        unit(unitId).failed(code, message);
        touch();
    }

    public synchronized void markAiActivity(String unitId, AnalysisAiActivityEvent event) {
        if (event == null) {
            return;
        }
        var details = new LinkedHashMap<>(event.details());
        details.put("deliveryUnitId", unitId);
        aiActivityEvents.add(new AnalysisAiActivityEvent(
                event.eventId(),
                event.parentEventId(),
                event.type(),
                event.category(),
                event.status(),
                event.title(),
                event.summary(),
                event.turnId(),
                event.interactionId(),
                event.toolCallId(),
                event.toolName(),
                event.timestamp(),
                details
        ));
        touch();
    }

    public synchronized void finalizeJob() {
        var allTerminal = units.values().stream().allMatch(DeliveryAssessmentUnitState::terminal);
        if (!allTerminal) {
            return;
        }
        var hasWarnings = units.values().stream().anyMatch(unit -> !"COMPLETED".equals(unit.status()));
        status = hasWarnings ? "COMPLETED_WITH_WARNINGS" : "COMPLETED";
        currentStepCode = "COMPLETED";
        currentStepLabel = hasWarnings ? "Completed with warnings" : "Completed";
        completedAt = Instant.now();
        touch();
    }

    public synchronized void markFailed(String code, String message) {
        status = "FAILED";
        currentStepCode = "FAILED";
        currentStepLabel = "Assessment failed";
        errorCode = code;
        errorMessage = message;
        completedAt = Instant.now();
        touch();
    }

    public synchronized DeliveryEffectivenessAssessmentJobStateSnapshot snapshot() {
        var unitSnapshots = units.values().stream().map(DeliveryAssessmentUnitState::snapshot).toList();
        var aggregate = aggregate(unitSnapshots);
        return new DeliveryEffectivenessAssessmentJobStateSnapshot(
                jobId,
                request.jiraProject(),
                request.fromDate(),
                request.toDate(),
                request.model(),
                request.reasoningEffort(),
                status,
                currentStepCode,
                currentStepLabel,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt,
                completedAt,
                discoveredIssues,
                processedIssues,
                totalIssues,
                effectiveJql,
                steps(unitSnapshots, aggregate),
                contextSections(unitSnapshots),
                List.copyOf(aiActivityEvents),
                unitSnapshots,
                aggregate,
                List.copyOf(new LinkedHashSet<>(visibilityLimits)),
                report(unitSnapshots, aggregate)
        );
    }

    private DeliveryAssessmentAggregateResponse aggregate(List<DeliveryAssessmentUnitResponse> snapshots) {
        var distribution = new LinkedHashMap<Integer, Integer>();
        var totalDsp = 0;
        var confidence = new ArrayList<Double>();
        for (var unit : snapshots) {
            if (unit.assessment() != null) {
                var dsp = unit.assessment().deliveredStoryPoints();
                distribution.merge(dsp, 1, Integer::sum);
                totalDsp += dsp;
                confidence.add(unit.assessment().confidence());
            }
        }
        var assessed = count(snapshots, "COMPLETED");
        var excluded = count(snapshots, "EXCLUDED");
        var notScorable = count(snapshots, "NOT_SCORABLE");
        var failed = count(snapshots, "FAILED");
        var coverage = snapshots.isEmpty() ? 0.0 : (double) assessed / snapshots.size();
        var averageConfidence = confidence.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return new DeliveryAssessmentAggregateResponse(
                totalDsp,
                distribution,
                snapshots.size(),
                assessed,
                excluded,
                notScorable,
                failed,
                Math.round(coverage * 1000.0) / 1000.0,
                confidenceLabel(averageConfidence),
                usage(snapshots)
        );
    }

    private AnalysisAiUsage usage(List<DeliveryAssessmentUnitResponse> snapshots) {
        var usages = snapshots.stream().map(DeliveryAssessmentUnitResponse::usage)
                .filter(java.util.Objects::nonNull).toList();
        if (usages.isEmpty()) {
            return null;
        }
        return new AnalysisAiUsage(
                usages.stream().mapToLong(AnalysisAiUsage::inputTokens).sum(),
                usages.stream().mapToLong(AnalysisAiUsage::outputTokens).sum(),
                usages.stream().mapToLong(AnalysisAiUsage::cacheReadTokens).sum(),
                usages.stream().mapToLong(AnalysisAiUsage::cacheWriteTokens).sum(),
                usages.stream().mapToLong(AnalysisAiUsage::totalTokens).sum(),
                usages.stream().mapToDouble(AnalysisAiUsage::cost).sum(),
                usages.stream().mapToLong(AnalysisAiUsage::apiDurationMs).sum(),
                usages.stream().mapToInt(AnalysisAiUsage::apiCallCount).sum(),
                usages.stream().map(AnalysisAiUsage::model).filter(java.util.Objects::nonNull).findFirst().orElse(null),
                usages.stream().map(AnalysisAiUsage::contextTokenLimit).filter(java.util.Objects::nonNull).max(Long::compareTo).orElse(null),
                usages.stream().map(AnalysisAiUsage::contextCurrentTokens).filter(java.util.Objects::nonNull).max(Long::compareTo).orElse(null),
                usages.stream().map(AnalysisAiUsage::contextMessages).filter(java.util.Objects::nonNull).max(Long::compareTo).orElse(null)
        );
    }

    private List<AnalysisJobStepResponse> steps(
            List<DeliveryAssessmentUnitResponse> snapshots,
            DeliveryAssessmentAggregateResponse aggregate
    ) {
        var discoveryStatus = discoveryCompletedAt != null ? "COMPLETED"
                : discoveryStartedAt != null ? "RUNNING" : "PENDING";
        var assessmentStatus = completedAt != null ? ("FAILED".equals(status) ? "FAILED" : "COMPLETED")
                : analysisStartedAt != null ? "RUNNING" : "PENDING";
        return List.of(
                new AnalysisJobStepResponse(
                        "JIRA_DISCOVERY", "Jira discovery", "CONTEXT", discoveryStatus,
                        processedIssues + " of " + discoveredIssues + " Jira issues processed.",
                        processedIssues, discoveryStartedAt, discoveryCompletedAt, List.of(), List.of()
                ),
                new AnalysisJobStepResponse(
                        "UNIT_ASSESSMENT", "Delivery Unit assessment", "AI", assessmentStatus,
                        snapshots.stream().filter(unit -> isTerminal(unit.status())).count()
                                + " of " + snapshots.size() + " units completed.",
                        snapshots.size(), analysisStartedAt, completedAt, List.of(), List.of(), aggregate.usage()
                )
        );
    }

    private List<AnalysisEvidenceSection> contextSections(List<DeliveryAssessmentUnitResponse> snapshots) {
        var scopeItems = new ArrayList<AnalysisEvidenceItem>();
        scopeItems.add(new AnalysisEvidenceItem("Jira scope", List.of(
                new AnalysisEvidenceAttribute("project", request.jiraProject()),
                new AnalysisEvidenceAttribute("fromDate", request.fromDate().toString()),
                new AnalysisEvidenceAttribute("toDate", request.toDate().toString()),
                new AnalysisEvidenceAttribute("effectiveJql", effectiveJql != null ? effectiveJql : "")
        )));
        var unitItems = snapshots.stream().map(unit -> new AnalysisEvidenceItem(
                unit.unitId(),
                List.of(
                        new AnalysisEvidenceAttribute("status", unit.status()),
                        new AnalysisEvidenceAttribute("issueCount", Integer.toString(unit.issues().size())),
                        new AnalysisEvidenceAttribute("mergeRequestCount", Integer.toString(unit.mergeRequests().size()))
                )
        )).toList();
        return List.of(
                new AnalysisEvidenceSection("jira", "delivery-scope", scopeItems),
                new AnalysisEvidenceSection("delivery", "delivery-units", unitItems)
        );
    }

    private AnalysisReport report(
            List<DeliveryAssessmentUnitResponse> snapshots,
            DeliveryAssessmentAggregateResponse aggregate
    ) {
        var lines = new StringBuilder();
        lines.append("- Total Delivered Story Points: **").append(aggregate.totalDeliveredStoryPoints()).append("**\n")
                .append("- Assessed: ").append(aggregate.assessedUnits()).append(" / ").append(aggregate.totalUnits()).append("\n")
                .append("- Coverage: ").append(Math.round(aggregate.coverage() * 100)).append("%\n")
                .append("- Confidence: ").append(aggregate.confidence()).append("\n");
        snapshots.stream().sorted(Comparator.comparing(DeliveryAssessmentUnitResponse::unitId)).forEach(unit -> {
            lines.append("\n### ").append(unit.unitId()).append("\n\n")
                    .append("- Status: ").append(unit.status()).append("\n");
            if (unit.assessment() != null) {
                lines.append("- DSP: ").append(unit.assessment().deliveredStoryPoints()).append("\n")
                        .append("- Confidence: ").append(unit.assessment().confidence()).append("\n");
            }
        });
        return new AnalysisReport(
                "delivery-effectiveness-" + jobId,
                "Delivery Effectiveness Assessment",
                request.jiraProject() + " | " + request.fromDate() + " - " + request.toDate(),
                "Observable delivered complexity with explicit coverage and visibility limits.",
                List.of(new AnalysisReportSection(
                        "ASSESSMENT_SUMMARY", "Assessment summary", 10, lines.toString(), AnalysisReportMeta.empty()
                )),
                new AnalysisReportMeta(
                        List.of(),
                        List.copyOf(new LinkedHashSet<>(visibilityLimits)),
                        List.of(),
                        List.of(),
                        aggregate.confidence(),
                        List.of()
                )
        );
    }

    private DeliveryAssessmentUnitState unit(String unitId) {
        var unit = units.get(unitId);
        if (unit == null) {
            throw new IllegalArgumentException("Unknown Delivery Unit: " + unitId);
        }
        return unit;
    }

    private int count(List<DeliveryAssessmentUnitResponse> snapshots, String status) {
        return (int) snapshots.stream().filter(unit -> status.equals(unit.status())).count();
    }

    private boolean isTerminal(String value) {
        return switch (value) {
            case "COMPLETED", "EXCLUDED", "NOT_SCORABLE", "FAILED" -> true;
            default -> false;
        };
    }

    private String confidenceLabel(double confidence) {
        if (confidence >= 0.8) {
            return "HIGH";
        }
        if (confidence >= 0.6) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private void touch() {
        updatedAt = Instant.now();
    }
}
